///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS com.google.api-client:google-api-client:1.23.0
//DEPS com.google.oauth-client:google-oauth-client-jetty:1.23.0
//DEPS com.google.apis:google-api-services-calendar:v3-rev305-1.23.0

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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

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
import com.google.api.services.calendar.model.EventDateTime;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExitCode;
import picocli.CommandLine.Option;

@Command(name = "jmeet", mixinStandardHelpOptions = true, version = "jmeet 0.1",
        description = "jmeet to get a fresh google meet link made with jbang")
class jmeet implements Callable<Integer> {

    @Option(names={"--credentials"}, defaultValue = "credentials.json", description = "path to credentials")
    private File credentials;

    @Option(names={"-c"}, defaultValue = "primary", description = "which calendar to use")
    private String calendar;

    @Option(names={"-b"}, description = "copy to clipBoard")
    boolean copyToClipboard;

    public static void main(String... args) {
        int exitCode = new CommandLine(new jmeet()).execute(args);
        System.exit(exitCode);
    }

   
    private static final String APPLICATION_NAME = "jmeet";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this quickstart.
     * If modifying these scopes, delete your previously saved tokens/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(CalendarScopes.CALENDAR);
    
    private Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(credentials);
        
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }


    public Integer call() throws Exception {
        Calendar service = null;
        try {
        // Build a new authorized API client service.
        final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
        service = new Calendar.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();
        } catch (FileNotFoundException fe) {
            System.err.println(fe);
            System.err.println("\nYou are missing credentials for accessing Google API's.\nDo the following:\n 1. Go to https://developers.google.com/calendar/quickstart/java\n 2. click 'Enable the Google Calendar API'\n 3. Download credentials.json and put it in current working directory.\n 4. run jmeet again");
            return ExitCode.USAGE;
        }
        Event event = new Event()
        .setSummary("jmeet")
        .setDescription("jmeet temporary event - can be deleted");

        DateTime startDateTime = new DateTime(new Date());
        EventDateTime start = new EventDateTime()
            .setDateTime(startDateTime);
            //.setTimeZone("America/Los_Angeles");
        event.setStart(start);

        DateTime endDateTime = new DateTime(new Date());
        EventDateTime end = new EventDateTime()
            .setDateTime(endDateTime);
           // .setTimeZone("America/Los_Angeles");
        event.setEnd(end);

        event.setConferenceData(new ConferenceData()
                                .setCreateRequest(new CreateConferenceRequest().setRequestId(UUID.randomUUID().toString())));

        event = service.events().insert(calendar, event).setConferenceDataVersion(1).execute();

        service.events().delete(calendar, event.getId()).execute();

        event.getConferenceData().getEntryPoints().forEach(x -> {
            if(x.getEntryPointType().equals("video")){
                if(x.getUri().startsWith("https://meet.google.com")) {
                    if(copyToClipboard){
                        copyClipboard(x.getUri());
                    } 
                  System.out.println(x.getUri());
                }
            }
        });

        return 0;
    }

    void copyClipboard(String str) {
             StringSelection stringSelection = new StringSelection(str);
             Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(stringSelection, null);
    }
}
