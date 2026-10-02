package com.jobtantra.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityValidationTest {

    private Validator validator;
    private AutoCloseable validatorFactory;

    @BeforeAll
    void setUpValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validatorFactory = factory::close;
    }

    @AfterAll
    void closeValidator() throws Exception {
        validatorFactory.close();
    }

    @Test
    void jobRequiresNameAndCreator() {
        Job job = new Job("", "description", "", 0, 60, RetryPolicy.defaults(), Map.of());

        assertThat(validator.validate(job))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .contains("name", "createdBy");
    }

    @Test
    void retryPolicyRejectsNegativeRetries() {
        RetryPolicy policy = new RetryPolicy(-1, 30, 60, java.math.BigDecimal.valueOf(2));

        assertThat(validator.validate(policy))
                .extracting("propertyPath")
                .extracting(Object::toString)
                .contains("maxRetries");
    }
}
