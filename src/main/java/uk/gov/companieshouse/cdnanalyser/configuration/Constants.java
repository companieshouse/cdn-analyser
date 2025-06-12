package uk.gov.companieshouse.cdnanalyser.configuration;

import java.time.ZoneId;

public class Constants {

    private Constants(){}

    public static ZoneId LONDON_ZONE_ID = ZoneId.of("Europe/London");
    public static final String APPLICATION_NAME_SPACE="cdn-analyser";
}
