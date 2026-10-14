package com.municipalidadsanjose.archivo.ocr;

import com.municipalidadsanjose.archivo.repository.DocumentoDigitalRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named="ocr.test.integration", matches="true")
@SpringJUnitConfig(OcrQueueIntegrationTest.Config.class)
class OcrQueueIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired DocumentoDigitalRepository documents;
    @org.springframework.beans.factory.annotation.Autowired DataSource source;
    JdbcTemplate jdbc;

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses=DocumentoDigitalRepository.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            String url=System.getProperty("ocr.test.db-url");
            if (!"jdbc:postgresql://127.0.0.1:55439/ocr_s4_isolated".equals(url)) {
                throw new IllegalStateException("Explicit isolated OCR database URL required; production access refused");
            }
            var data=new DriverManagerDataSource(url,"ocr_s4_test","ocr-s4-test-only");
            var jdbc=new JdbcTemplate(data);
            if (!"ocr_s4_isolated".equals(jdbc.queryForObject("select current_database()",String.class))
                    || !"owned-by-ocr-s4-only".equals(jdbc.queryForObject("select marker from ocr_isolated_test_marker",String.class))) {
                throw new IllegalStateException("Isolated marker missing; all schema writes refused");
            }
            jdbc.execute("DROP TABLE IF EXISTS documento_digital");
            jdbc.execute("CREATE TABLE documento_digital (documento_id uuid primary key, ocr_estado varchar(30) not null, fecha_digitalizacion timestamp not null default current_timestamp, ocr_intentos integer not null default 0, ocr_actualizado_en timestamp, ocr_error text, ocr_texto text, ocr_confianza numeric(5,4), ocr_paginas integer, ocr_revisado boolean not null default false, ocr_layout text, ruta_pdf varchar(500), ocr_version bigint not null default 0)");
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V13__ocr_jobs_progress.sql")));
            return data;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource data) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(data);
            factory.setPackagesToScan("com.municipalidadsanjose.archivo.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","none","hibernate.show_sql","false"));
            return factory;
        }
        @Bean JpaTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @BeforeEach void resetFixture() {
        jdbc=new JdbcTemplate(source);
        jdbc.update("DELETE FROM documento_digital");
    }

    UUID insert(String state) {
        UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO documento_digital(documento_id,ocr_estado) VALUES (?,?)",id,state); return id;
    }
    String owner(char value) { return String.valueOf(value).repeat(32); }
    void identity(UUID id,String lease) {
        assertEquals(1,documents.guardarJobIdentity(id,lease,owner('a'),"b".repeat(64),"c".repeat(64)));
    }

    @Test void moreThanOneHundredPendingRowsContinuouslyDrainWithoutErrorRows() {
        for(int i=0;i<150;i++) insert("PENDIENTE");
        UUID historical=insert("ERROR"); Set<UUID> seen=new HashSet<>();
        while(true) {
            var batch=documents.reclamarSiguiente(owner('e'),120); if(batch.isEmpty()) break;
            UUID id=batch.get(0); assertTrue(seen.add(id)); identity(id,owner('e'));
            assertEquals(1,documents.publicarJob(id,owner('e'),owner('a'),"text",new BigDecimal("0.9"),1,"COMPLETADO","[]","synthetic.pdf"));
        }
        assertEquals(150,seen.size()); assertFalse(seen.contains(historical));
    }

    @Test void competingClaimsHaveExactlyOneWinner() throws Exception {
        UUID id=insert("PENDIENTE"); var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await(); return documents.reclamarSiguiente(owner('e'),120);});
            var b=pool.submit(()->{start.await(); return documents.reclamarSiguiente(owner('f'),120);});
            start.countDown(); var first=a.get(10,TimeUnit.SECONDS); var second=b.get(10,TimeUnit.SECONDS);
            assertEquals(1,first.size()+second.size()); assertEquals(id,first.isEmpty()?second.get(0):first.get(0));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10,TimeUnit.SECONDS)); }
    }

    @Test void liveLeaseSurvivesRestartAndExpiredLeaseIsRecoverable() {
        UUID id=insert("PROCESANDO");
        jdbc.update("UPDATE documento_digital SET ocr_lease_owner=?,ocr_lease_expires_at=CURRENT_TIMESTAMP+interval '1 minute' WHERE documento_id=?",owner('e'),id);
        assertTrue(documents.reclamarSiguiente(owner('f'),120).isEmpty());
        jdbc.update("UPDATE documento_digital SET ocr_lease_expires_at=CURRENT_TIMESTAMP-interval '1 second' WHERE documento_id=?",id);
        assertEquals(List.of(id),documents.reclamarSiguiente(owner('f'),120));
        assertEquals(0,documents.renovarLease(id,owner('e'),120));
        assertEquals(1,documents.renovarLease(id,owner('f'),120));
    }

    @Test void publicationIsFencedByJobLeaseManualRetryAndDeletion() {
        UUID id=insert("PENDIENTE"); documents.reclamarSiguiente(owner('e'),120); identity(id,owner('e'));
        assertEquals(0,documents.publicarJob(id,owner('f'),owner('a'),"wrong",BigDecimal.ONE,1,"COMPLETADO","[]","wrong.pdf"));
        assertEquals(0,documents.publicarJob(id,owner('e'),owner('d'),"wrong",BigDecimal.ONE,1,"COMPLETADO","[]","wrong.pdf"));
        jdbc.update("UPDATE documento_digital SET ruta_pdf='old.pdf' WHERE documento_id=?",id);
        documents.reiniciarJob(id);
        assertEquals(0,documents.publicarJob(id,owner('e'),owner('a'),"stale",BigDecimal.ONE,1,"COMPLETADO","[]","new.pdf"));
        assertEquals("old.pdf",jdbc.queryForObject("SELECT ruta_pdf FROM documento_digital WHERE documento_id=?",String.class,id));
        documents.reclamarSiguiente(owner('f'),120); identity(id,owner('f'));
        jdbc.update("DELETE FROM documento_digital WHERE documento_id=?",id);
        assertEquals(0,documents.publicarJob(id,owner('f'),owner('a'),"deleted",BigDecimal.ONE,1,"COMPLETADO","[]","new.pdf"));
    }

    @Test void retryBackoffAndProgressPersistWithoutClearingIdentity() {
        UUID id=insert("PENDIENTE"); documents.reclamarSiguiente(owner('e'),120); identity(id,owner('e'));
        assertEquals(1,documents.guardarProgreso(id,owner('e'),owner('a'),3,10,"RECOGNIZING",30));
        assertTrue(documents.reclamarSiguiente(owner('f'),120).isEmpty());
        Double seconds=jdbc.queryForObject("SELECT extract(epoch from ocr_next_retry_at-CURRENT_TIMESTAMP) FROM documento_digital WHERE documento_id=?",Double.class,id);
        assertTrue(seconds > 20 && seconds <= 30);
        assertEquals(3,jdbc.queryForObject("SELECT ocr_paginas_procesadas FROM documento_digital WHERE documento_id=?",Integer.class,id));
        assertEquals(owner('a'),jdbc.queryForObject("SELECT ocr_job_id FROM documento_digital WHERE documento_id=?",String.class,id));
    }

    @Test void retryBeforeRemoteIdentityUsesDatabaseClockAndLiveLeaseFence() {
        UUID id=insert("PENDIENTE"); documents.reclamarSiguiente(owner('e'),120);
        assertEquals(1,documents.guardarFalloJob(id,owner('e'),null,"PROCESANDO","safe","RETRY_WAIT",30));
        assertEquals(1,jdbc.queryForObject("SELECT ocr_retries FROM documento_digital WHERE documento_id=?",Integer.class,id));
        assertEquals(0,documents.guardarFalloJob(id,owner('e'),null,"ERROR","stale","ERROR",0));
        Double seconds=jdbc.queryForObject("SELECT extract(epoch from ocr_next_retry_at-CURRENT_TIMESTAMP) FROM documento_digital WHERE documento_id=?",Double.class,id);
        assertTrue(seconds > 20 && seconds <= 30);
    }
}
