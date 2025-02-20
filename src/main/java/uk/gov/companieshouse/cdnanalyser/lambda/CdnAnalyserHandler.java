package uk.gov.companieshouse.cdnanalyser.lambda;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import uk.gov.companieshouse.cdnanalyser.CdnAnalyserApplication;
import uk.gov.companieshouse.cdnanalyser.service.AbstractProcessor;

public class CdnAnalyserHandler implements RequestHandler<Map<String, Object>, String> {

    private AbstractProcessor abstractProcessor;

    @Override
    public String handleRequest(Map<String, Object> input, Context lambdaContext) {


        if(input.containsKey("properties")){
            Map<String, String> properties = (Map<String,String>) input.get("properties");
            properties.forEach(System::setProperty);
        }

        ConfigurableApplicationContext context = SpringApplication.run(CdnAnalyserApplication.class);
        this.abstractProcessor = context.getBean(AbstractProcessor.class);

        abstractProcessor.handleAssets();

        return "The CDN analysis Lambda has been triggered";
    }
}
