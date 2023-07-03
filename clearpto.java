///usr/bin/env jbang "$0" "$@" ; exit $?
//DESCRIPTION clearpto to clear your calendar for a PTO period
//DEPS info.picocli:picocli:4.5.0
//DEPS com.google.api-client:google-api-client:1.23.0
//DEPS com.google.oauth-client:google-oauth-client-jetty:1.23.0
//DEPS com.google.apis:google-api-services-calendar:v3-rev305-1.23.0
//DEPS org.ocpsoft.prettytime:prettytime:4.0.6.Final
//DEPS org.ocpsoft.prettytime:prettytime-nlp:4.0.6.Final

import java.awt.datatransfer.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.ConferenceData;
import com.google.api.services.calendar.model.CreateConferenceRequest;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.Events;

import org.ocpsoft.prettytime.Duration;
import org.ocpsoft.prettytime.PrettyTime;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.ocpsoft.prettytime.shade.edu.emory.mathcs.backport.java.util.Arrays;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "clearpto", mixinStandardHelpOptions = true, version = "clearpto 0.1", description = "clearpto to clear your calendar for a PTO period made by jbang")
class clearpto implements Callable<Integer> {

    @Option(names = { "--credentials" }, defaultValue = "credentials.json", description = "path to credentials")
    private File credentials;

    @Option(names = { "-c" }, defaultValue = "primary", description = "which calendar to use")
    private String calendar;

    @Option(names = { "--period" }, defaultValue = "next week", description = "Which period to take PTO")
    private String period;

    @Option(names = { "--email" }, required = true, description = "Which email to use for declining and deleting events")
    private String email;

    @Option(names = { "--comment" }, defaultValue = "on PTO" , description = "Comment to put on the response")
    private String comment;

    @Option(names = { "--force"}, defaultValue = "false", description = "Set to actually decline/delete")
    boolean force;

    public static void main(String... args) {
        int exitCode = new CommandLine(new clearpto()).execute(args);
        System.exit(exitCode);
    }

    private static final String APPLICATION_NAME = "clearpto";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart. If modifying these
     * scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);

    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(credentials);

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY,
                clientSecrets, SCOPES)
                        .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                        .setAccessType("offline").build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public Integer call() throws Exception {
        
        PrettyTime pt = new PrettyTime();
        PrettyTimeParser ptp = new PrettyTimeParser();

        List<Date> dates = ptp.parse(period);
        System.out.println("from: " + dates);
        Date from = dates.get(0);
        Date to = dates.get(dates.size() - 1);

        String msg = String.format("Looking for events from %s (%s) to %s (%s)",
                        from,
                        pt.format(from),
                        to,
                        pt.format(to));
        System.out.println(msg);

        Calendar service = null;
        try {
            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME).build();
        } catch (FileNotFoundException fe) {
            System.err.println(fe);
            System.err.println(
                    "\nYou are missing credentials for accessing Google API's.\nDo the following:\n 1. Go to https://developers.google.com/calendar/quickstart/java\n 2. click 'Enable the Google Calendar API'\n 3. Download credentials.json and put it in current working directory.\n 4. run jmeet again");
            return ExitCode.USAGE;
        }

        // email to number of events
        Map<String, Integer> attendees = new HashMap<>();

        String pageToken = null;
        do {
            DateTime startDateTime = new DateTime(from);
            DateTime endDateTime = new DateTime(to);

            Events events = service.events().list(calendar)
                            .setPageToken(pageToken)
                            .setTimeMin(startDateTime)
                            .setTimeMax(endDateTime)
                            .setShowDeleted(false)
                            .setShowHiddenInvitations(false)
                            .setSingleEvents(true)
                            .execute();
            List<Event> items = events.getItems()
                        .stream()
                        //ignore meetings that are already cancelled
                        .filter(e -> !"cancelled".equals(e.getStatus()))
                        //ignore meetings you already declined
                        .filter(e -> {
                            if(e.getAttendees()!=null) {
                               return e.getAttendees().stream().noneMatch(a -> a.isSelf() && "declined".equals(a.getResponseStatus()));
                            } else {
                                return true;
                            }
                        })
                        .collect(Collectors.toList());

            for (Event event : items) {
                if("cancelled".equals(event.getStatus())) {
                    continue;
                }

                String who = event.getCreator().getEmail();
                String title = Objects.toString(event.getSummary(), "<no summary>");
                String desc = Objects.toString(event.getDescription(), "<no description>").replaceAll("\\<.*?\\>", "");
                int guests = event.getAttendees()==null?0:event.getAttendees().size();
                //System.out.println(event + "\n\n");

                if(event.getAttendees()!=null) {
                event.getAttendees().forEach(a -> {
                    if(!a.isSelf()) {
                        attendees.merge(a.getEmail(), 1, (o,n) -> o == null ? 1 : o+n);
                    }
                });
                }
            
                String emsg = String.format("'%s' - '%1.16s' by %s with %d guests", title, desc, who, guests);
                
                if(force) {
                if(!event.getCreator().isSelf()) {

                    System.out.println("DECLINE " + emsg);
                    final Event eventToDecline = new Event().setAttendees(List.of(
                        new EventAttendee().setEmail(email)
                        .setResponseStatus("declined")
                        .setComment(comment)));
                    service.events().patch(calendar, event.getId(), eventToDecline).execute();
                } else {
                    System.out.println("DELETE " + emsg);
                    service.events().delete(calendar, event.getId()).execute();
                }
             } else {
                    System.out.println("NOOP " + emsg);
                }
            }
            
            pageToken = events.getNextPageToken();
        } while (pageToken != null);

        System.out.println("Found " + attendees.size() + " attendees spread over the following domains:");
        Map<String, Integer> domain = new HashMap<>();
        attendees.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEach(e -> {
            domain.merge(e.getKey().split("@")[1], 1, (o,n) -> o+n);
        });
        domain.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).forEach(e -> {
            System.out.println(e.getKey() + " " + e.getValue());
        });
        if(!force) {
            System.out.println("To decline these meetings, run with --force");
        }


        return 0;
    }


    void copyClipboard(String str) {
        StringSelection stringSelection = new StringSelection(str);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }
}
