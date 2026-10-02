package com.jobtantra;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JobTantraApplicationTests {

    @Test
    void applicationClassIsPresent() {
        assertTrue(JobTantraApplication.class.isAnnotationPresent(
                org.springframework.boot.autoconfigure.SpringBootApplication.class));
    }
}
