package com.mathio.lms;

import static org.junit.jupiter.api.Assertions.*;

import com.mathio.lms.model.MathAnswer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathAnswerTest {
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "x+1|1+x",
                "S={1;2}|{2;1}",
                "2*(x+1)|2*x+2",
                "(x+y)^2|x^2+2*x*y+y^2",
                "1/2|0.5",
                "-2^2|-4",
                "(1/3+2/3)^2|1",
                "\\frac{1}{2}+\\frac{1}{4}|0.75",
                "\\frac{x+1}{2}|0.5*x+0.5",
                "x^{2}+2*x+1|(1+x)^2",
                "{1;2;1}|{2;1}",
                "x^2=1|{-1;1}",
                "2*x+2=4|1",
                "x=1|1=x",
                "x^2-2*x+1=0|{1}",
                "x^2+1=0|{}",
                "x\\in\\{1;2\\}|{2;1}",
                "1/(x+1)|1/(1+x)"
            })
    void provesEquivalenceExactly(String a, String b) {
        assertEquals(MathAnswer.Comparison.EQUIVALENT, MathAnswer.compare(a, b));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {"x+1|x-1", "x^2|x", "1/3|0.333333333", "{1;2}|{1;3}", "x^2=1|{1}", "-2^2|4"})
    void rejectsNonEquivalentExpressions(String a, String b) {
        assertEquals(MathAnswer.Comparison.NOT_EQUIVALENT, MathAnswer.compare(a, b));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "x/x|1",
                "0^0|1",
                "{x/x;1}|{1}",
                "x=1|y=1",
                "(x^2-1)/(x-1)|x+1",
                "sin(x)|sin(x)",
                "1/0|1/0",
                "x^2=2|{1.414}",
                "x^100000|1",
                "\\sqrt{2}|\\sqrt{2}"
            })
    void unsupportedOrChangedDomainsStayUnknown(String a, String b) {
        assertEquals(MathAnswer.Comparison.UNKNOWN, MathAnswer.compare(a, b));
    }

    @Test
    void rejectsResourceExhaustion() {
        assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(1),
                () -> {
                    assertEquals(
                            MathAnswer.Comparison.UNKNOWN,
                            MathAnswer.compare("(".repeat(400) + "1" + ")".repeat(400), "1"));
                    assertEquals(
                            MathAnswer.Comparison.UNKNOWN,
                            MathAnswer.compare("9".repeat(1001), "1"));
                });
    }
}
