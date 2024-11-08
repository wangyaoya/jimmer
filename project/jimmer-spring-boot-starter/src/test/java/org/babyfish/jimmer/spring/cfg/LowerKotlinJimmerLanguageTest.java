package org.babyfish.jimmer.spring.cfg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = {SqlClientConfig.class, JimmerLanguageTestHelper.DataSourceConfig.class},
        properties = {"jimmer.language=kotlin"}
)
@EnableConfigurationProperties(JimmerProperties.class)
public class LowerKotlinJimmerLanguageTest {
    @Value("${jimmer.language}")
    private String jimmerLanguage;

    @Autowired(required = false)
    @Qualifier("sqlClient")
    protected Object sqlClient;

    @Test
    public void javaLanguage(@Autowired JimmerProperties jimmerProperties) {
        JimmerLanguageTestHelper.kotlinLanguage(sqlClient);
        Assertions.assertEquals("kotlin", jimmerLanguage);
        Assertions.assertEquals("kotlin", jimmerProperties.getLanguage());
    }
}