package com.pertegato.propertyflow.triage;

import com.pertegato.propertyflow.triage.service.AnalyzeTool.ErrorCode;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Failure;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Request;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Success;
import com.pertegato.propertyflow.triage.service.AnalyzeTool.Urgency;
import com.pertegato.propertyflow.triage.service.AnalyzeTool;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class AnalyzeToolTest {

    @Test
    void successfulCallReturnsTypedAssessment() {
        AnalyzeTool tool = new AnalyzeTool(text -> " HOCH\n");
        assertEquals(new Success(Urgency.HOCH), tool.analyze(new Request("Heizung ausgefallen.")));
    }

    @Test
    void invalidInputsNeverReachModel() {
        AtomicInteger calls = new AtomicInteger();
        AnalyzeTool tool = new AnalyzeTool(text -> {
            calls.incrementAndGet();
            return "NORMAL";
        });

        Request[] invalidRequests = {
                null,
                new Request(null),
                new Request(""),
                new Request(" \n\t"),
                new Request("x".repeat(4001))
        };
        for (Request request : invalidRequests) {
            assertEquals(ErrorCode.INVALID_INPUT, ((Failure) tool.analyze(request)).code());
        }

        assertEquals(0, calls.get());
        assertInstanceOf(Success.class, tool.analyze(new Request("x".repeat(4000))));
        assertEquals(1, calls.get());
    }

    @Test
    void providerFailureDoesNotLeakDetails() {
        AnalyzeTool tool = new AnalyzeTool(text -> {
            throw new IllegalStateException("secret-key tenant-data");
        });

        Failure response = (Failure) tool.analyze(new Request("Heizung ausgefallen."));
        assertEquals(ErrorCode.MODEL_UNAVAILABLE, response.code());
        assertEquals("KI-Analyse derzeit nicht verfügbar. Bitte manuell prüfen.", response.message());
    }

    @Test
    void untrustedOutputIsRejectedWithoutDefaultUrgency() {
        String[] invalidOutputs = {
                null,
                "",
                "DRINGEND",
                "NORMAL <script>alert(1)</script>",
                "normal",
                "HOCH\nBegründung",
                "x".repeat(10000)
        };
        for (String output : invalidOutputs) {
            AnalyzeTool tool = new AnalyzeTool(text -> output);
            assertEquals(ErrorCode.INVALID_MODEL_OUTPUT, ((Failure) tool.analyze(new Request("Testanliegen"))).code());
        }
    }
}
