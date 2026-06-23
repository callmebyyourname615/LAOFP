package com.example.switching.observability.sql;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class NPlusOneStatementInspectorTest {
    @AfterEach
    void disable() { NPlusOneStatementInspector.configure(false, 25, 5); }

    @Test
    void fingerprintsHideLiteralValues() {
        String value = NPlusOneStatementInspector.fingerprint(
                "select * from accounts where account_no='123456789' and id=99");
        assertThat(value).doesNotContain("123456789").doesNotContain("99");
    }

    @Test
    void countsRepeatedStatementsWithinRequest() {
        NPlusOneStatementInspector.configure(true, 25, 3);
        var inspector = new NPlusOneStatementInspector();
        NPlusOneStatementInspector.beginRequest();
        inspector.inspect("select * from child where parent_id=1");
        inspector.inspect("select * from child where parent_id=2");
        inspector.inspect("select * from child where parent_id=3");
        var snapshot = NPlusOneStatementInspector.endRequest();
        assertThat(snapshot.totalQueries()).isEqualTo(3);
        assertThat(snapshot.maxRepeatedCount()).isEqualTo(3);
    }
}
