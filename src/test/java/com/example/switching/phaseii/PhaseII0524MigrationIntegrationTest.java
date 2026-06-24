package com.example.switching.phaseii;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import com.example.switching.AbstractIntegrationTest;

class PhaseII0524MigrationIntegrationTest extends AbstractIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Test void migrationsReachV96AndCreateAllPhaseIIModules(){
        // Phase II Phase 05-24 migrations were renumbered from V85-V90 to V91-V96
        // because V85-V87 were reserved for read-scaling work landed earlier.
        // V96 is the final Phase II migration (rtp_authorisation_settlement_extensions).
        Integer v96=jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='96' AND success",Integer.class);
        assertEquals(1,v96);
        for(String table:new String[]{"promotion","push_payment_policy","cross_border_rail_message","report_delivery_schedule"}){
            Integer count=jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=current_schema() AND table_name=?",Integer.class,table);
            assertEquals(1,count,table);
        }
        String shaType=jdbc.queryForObject("SELECT data_type FROM information_schema.columns WHERE table_schema=current_schema() AND table_name='report_artifact' AND column_name='content_sha256'",String.class);
        assertEquals("character varying",shaType);
    }
}
