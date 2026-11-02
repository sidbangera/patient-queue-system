package com.patientqueue.system.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in. Uses only randomly named schemas in a dedicated *_test database. */
@EnabledIfEnvironmentVariable(named="PQ_RUN_DB_TESTS", matches="true")
class PostgresIntegrationTest {
    private String url,user,password,schema;
    private DriverManagerDataSource dataSource;
    @BeforeEach void setup() throws Exception {
        url=Objects.requireNonNull(System.getenv("PQ_TEST_JDBC_URL"),"Set PQ_TEST_JDBC_URL to a dedicated *_test database");
        user=Objects.requireNonNullElse(System.getenv("PQ_TEST_DB_USERNAME"),"postgres");
        password=Objects.requireNonNull(System.getenv("PQ_TEST_DB_PASSWORD"),"Set PQ_TEST_DB_PASSWORD");
        schema="pq_it_"+UUID.randomUUID().toString().replace("-","");
        try(Connection c=DriverManager.getConnection(url,user,password);Statement st=c.createStatement()) {
            try(ResultSet rs=st.executeQuery("SELECT current_database()")) {
                rs.next(); assertTrue(rs.getString(1).endsWith("_test"),"Refusing to modify a database not ending in _test");
            }
            st.execute("CREATE SCHEMA "+schema);
        }
        dataSource=new DriverManagerDataSource(url+(url.contains("?")?"&":"?")+"currentSchema="+schema,user,password);
        try(Connection c=dataSource.getConnection();Statement st=c.createStatement()) {
            st.execute("CREATE TABLE doctor(id bigint primary key,name text,department text)");
            st.execute("INSERT INTO doctor VALUES(1,'Doctor A','General'),(2,'Doctor B','General')");
            st.execute("CREATE TABLE daily_queue_counter(doctor_id bigint,service_date date,last_token_number int, UNIQUE(doctor_id,service_date))");
            st.execute("CREATE TABLE queue_entry(id bigint generated always as identity primary key,patient_id bigint,doctor_id bigint,service_date date not null,token_number int,status text,check_in_time timestamp,consultation_start_time timestamp,consultation_end_time timestamp,UNIQUE(doctor_id,service_date,token_number))");
            st.execute("CREATE UNIQUE INDEX uq_active ON queue_entry(patient_id,doctor_id,service_date) WHERE status IN ('WAITING','IN_CONSULTATION')");
            st.execute("CREATE UNIQUE INDEX uq_consultation ON queue_entry(doctor_id,service_date) WHERE status='IN_CONSULTATION'");
        }
    }
    @AfterEach void cleanup() throws Exception {
        if(dataSource==null)return;
        try(Connection c=DriverManager.getConnection(url,user,password);Statement st=c.createStatement()) {
            if(schema.matches("pq_it_[a-f0-9]{32}"))st.execute("DROP SCHEMA "+schema+" CASCADE");
        }
    }
    @Test void atomicCounterAllocatesUniqueTokensAcrossConnections() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Integer>> tasks=new ArrayList<>();
            for(int i=0;i<24;i++)tasks.add(()-> {
                try(Connection c=dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    int n;
                    try(Statement s=c.createStatement();ResultSet rs=s.executeQuery("INSERT INTO daily_queue_counter VALUES(1,DATE '2026-10-31',1) ON CONFLICT(doctor_id,service_date) DO UPDATE SET last_token_number=daily_queue_counter.last_token_number+1 RETURNING last_token_number")) {rs.next();n=rs.getInt(1);}
                    c.commit();return n;
                }
            });
            Set<Integer> tokens=new HashSet<>();for(Future<Integer> f:executor.invokeAll(tasks))tokens.add(f.get(20,TimeUnit.SECONDS));
            assertEquals(24,tokens.size());assertTrue(tokens.containsAll(List.of(1,24)));
        }finally{executor.shutdownNow();}
    }
    @Test void realAnalyticsQueriesRespectFiltersAndZeroDays() throws Exception {
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement()) {
            s.execute("INSERT INTO queue_entry(patient_id,doctor_id,service_date,token_number,status,check_in_time,consultation_start_time,consultation_end_time) VALUES " +
              "(1,1,'2026-10-31',1,'COMPLETED','2026-10-31 10:00','2026-10-31 10:10','2026-10-31 10:15'),"+
              "(2,1,'2026-10-31',2,'WAITING','2026-10-31 11:00',NULL,NULL),"+
              "(3,2,'2026-10-31',1,'NO_SHOW','2026-10-31 12:00',NULL,NULL)");
        }
        AnalyticsService service=new AnalyticsService(new NamedParameterJdbcTemplate(dataSource));
        LocalDate from=LocalDate.of(2026,10,30),to=LocalDate.of(2026,10,31);
        Map<String,Object> report=service.summary(from,to,1L);
        assertEquals(2L,report.get("total"));assertEquals(50.0,report.get("completionRatePct"));
        assertEquals(10.0,report.get("avgWaitMinutes"));assertEquals(5.0,report.get("avgConsultationMinutes"));
        List<Map<String,Object>> days=service.daily(from,to,1L);assertEquals(2,days.size());assertEquals(0L,days.get(0).get("checkins"));
        assertEquals(24,service.hourly(from,to,1L).size());assertEquals(1,service.doctors(from,to,1L).size());
        assertEquals(0L,service.dataQuality(from,to,1L).get("flaggedVisits"));
        assertTrue(service.exportDoctorDayCsv(from,to,1L).contains("2026-10-31,1,2,1,0"));
        assertNull(service.summary(from.minusYears(1),to.minusYears(1),1L).get("completionRatePct"));
    }
    @Test void databaseRejectsDuplicateActiveVisitAndAllowsRepeatAfterNoShow() throws Exception {
        try(Connection c=dataSource.getConnection();Statement s=c.createStatement()) {
            s.execute("INSERT INTO queue_entry(patient_id,doctor_id,service_date,token_number,status) VALUES(1,1,'2026-10-31',1,'WAITING')");
            SQLException e=assertThrows(SQLException.class,()->s.execute("INSERT INTO queue_entry(patient_id,doctor_id,service_date,token_number,status) VALUES(1,1,'2026-10-31',2,'WAITING')"));assertEquals("23505",e.getSQLState());
            s.execute("UPDATE queue_entry SET status='NO_SHOW' WHERE token_number=1");
            assertEquals(1,s.executeUpdate("INSERT INTO queue_entry(patient_id,doctor_id,service_date,token_number,status) VALUES(1,1,'2026-10-31',2,'WAITING')"));
        }
    }
    @Test void rowLockSerializesConflictingStateReaders() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(Connection a=dataSource.getConnection();Statement sa=a.createStatement()) {
            sa.execute("INSERT INTO queue_entry(patient_id,doctor_id,service_date,token_number,status) VALUES(1,1,'2026-10-31',1,'WAITING')");
            a.setAutoCommit(false);sa.executeQuery("SELECT * FROM queue_entry WHERE id=1 FOR UPDATE").close();
            sa.executeUpdate("UPDATE queue_entry SET status='IN_CONSULTATION' WHERE id=1");
            Future<String> next=executor.submit(()->{try(Connection b=dataSource.getConnection();Statement sb=b.createStatement()) {b.setAutoCommit(false);String status;try(ResultSet rs=sb.executeQuery("SELECT status FROM queue_entry WHERE id=1 FOR UPDATE")){rs.next();status=rs.getString(1);}b.commit();return status;}});
            assertThrows(TimeoutException.class,()->next.get(200,TimeUnit.MILLISECONDS));
            a.commit();assertEquals("IN_CONSULTATION",next.get(10,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();}
    }
}
