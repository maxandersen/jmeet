# jmeet - utilities for working with google calendar.

## jmeet - quickly generate a google meet url

`jmeet`, a little quick utility created with one purpose: Get a google meet url without  relying on a browser nor custom integration available.

    jbang run@maxandersen/jbangdev

## clearpto - clear out your calendar for a period, i.e. for a vacation.

`clearpto`, will take a period (expressed in natural language, like "tomorrow to 24th December") and find any event that are not already canceled, deleted or declined and delete or cancel them as needed. It will add a comment about "being on pto" by default, but you can change it.

     jbang clearpto@maxandersen/jmeet --comment="I'm on vacation. peace" --email=your@ma.il --period "tomorrow to 10th January 2021"

The command will not actually do any changes until you run with `--force` so you can check which events gets affected.

## To run these scripts

How to run it without anything installed:

    curl -Ls https://sh.jbang.dev | bash -s - <aliasname>@maxandersen/jmeet

If you have [jbang](https://jbang.dev) already installed just do:

    jbang <aliasname>@maxandersen/jmeet

To install it via jbang do:

    jbang app install --name jmeet <aliasname>@maxandersen/jmeet
