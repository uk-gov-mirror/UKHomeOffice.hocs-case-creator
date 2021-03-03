package uk.gov.digital.ho.hocs.queue;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws.sqs.SqsConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static uk.gov.digital.ho.hocs.application.RequestData.transferHeadersToMDC;

@Component
public class UKVIComplaintConsumer extends RouteBuilder {

    private final UKVIComplaintService UKVIComplaintService;
    private final String ukviComplaintQueue;
    private final String dlq;
    private final int maximumRedeliveries;
    private final int redeliveryDelay;
    private final int backOffMultiplier;

    @Autowired
    public UKVIComplaintConsumer(UKVIComplaintService UKVIComplaintService,
                                 @Value("${ukvi-complaint.queue}") String ukviComplaintQueue,
                                 @Value("${ukvi-complaint.queue.dlq}") String dlq,
                                 @Value("${ukvi-complaint.queue.maximumRedeliveries}") int maximumRedeliveries,
                                 @Value("${ukvi-complaint.queue.redeliveryDelay}") int redeliveryDelay,
                                 @Value("${ukvi-complaint.queue.backOffMultiplier}") int backOffMultiplier) {
        this.UKVIComplaintService = UKVIComplaintService;
        this.ukviComplaintQueue = ukviComplaintQueue;
        this.dlq = dlq;
        this.maximumRedeliveries = maximumRedeliveries;
        this.redeliveryDelay = redeliveryDelay;
        this.backOffMultiplier = backOffMultiplier;
    }

    @Override
    public void configure() {

        errorHandler(deadLetterChannel(dlq)
                .loggingLevel(LoggingLevel.ERROR)
                .retryAttemptedLogLevel(LoggingLevel.WARN)
                .useOriginalMessage()
                .maximumRedeliveries(maximumRedeliveries)
                .redeliveryDelay(redeliveryDelay)
                .backOffMultiplier(backOffMultiplier)
                .asyncDelayedRedelivery()
                .logRetryStackTrace(false)
                .onPrepareFailure(exchange -> {
                    exchange.getIn().setHeader("FailureMessage", exchange.getProperty(Exchange.EXCEPTION_CAUGHT,
                            Exception.class).getMessage());
                    exchange.getIn().setHeader(SqsConstants.RECEIPT_HANDLE, exchangeProperty(SqsConstants.RECEIPT_HANDLE));
                }));

        from(ukviComplaintQueue)
                .setProperty(SqsConstants.RECEIPT_HANDLE, header(SqsConstants.RECEIPT_HANDLE))
                .process(transferHeadersToMDC())
                .log(LoggingLevel.INFO, "UKVI Complaint request received")
                .log(LoggingLevel.DEBUG, "UKVI Complaint unmarshalled")
                .bean(UKVIComplaintService, "createComplaint(${body})")
                .log(LoggingLevel.INFO, "UKVI Complaint request processed")
                .setHeader(SqsConstants.RECEIPT_HANDLE, exchangeProperty(SqsConstants.RECEIPT_HANDLE));
    }
}