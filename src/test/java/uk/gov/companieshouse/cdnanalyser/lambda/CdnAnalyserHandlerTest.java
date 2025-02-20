package uk.gov.companieshouse.cdnanalyser.lambda;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import com.amazonaws.services.lambda.runtime.Context;
import uk.gov.companieshouse.cdnanalyser.CdnAnalyserApplication;
import uk.gov.companieshouse.cdnanalyser.service.AbstractProcessor;

public class CdnAnalyserHandlerTest {

    @Mock
    private AbstractProcessor abstractProcessor;

    @Mock
    private ConfigurableApplicationContext context;

    @InjectMocks
    private CdnAnalyserHandler cdnAnalyserHandler;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(context.getBean(AbstractProcessor.class)).thenReturn(abstractProcessor);
    }

    @Test
    public void testHandleRequestWithProperties() {
        Map<String, Object> input = new HashMap<>();
        Map<String, String> properties = new HashMap<>();
        properties.put("key", "value");
        input.put("properties", properties);

        Context lambdaContext = mock(Context.class);

        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(CdnAnalyserApplication.class)).thenReturn(context);

            String result = cdnAnalyserHandler.handleRequest(input, lambdaContext);

            verify(abstractProcessor).handleAssets();
            assertEquals("The CDN analysis Lambda has been triggered", result);
        }
    }

    @Test
    public void testHandleRequestWithoutProperties() {
        Map<String, Object> input = new HashMap<>();

        Context lambdaContext = mock(Context.class);

        try (MockedStatic<SpringApplication> mockedSpringApplication = mockStatic(SpringApplication.class)) {
            mockedSpringApplication.when(() -> SpringApplication.run(CdnAnalyserApplication.class)).thenReturn(context);

            String result = cdnAnalyserHandler.handleRequest(input, lambdaContext);

            verify(abstractProcessor).handleAssets();
            assertEquals("The CDN analysis Lambda has been triggered", result);
        }
    }
}