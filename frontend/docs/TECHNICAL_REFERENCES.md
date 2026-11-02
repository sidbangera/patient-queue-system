# Primary technical references

Implementation decisions in this package should be read together with the shipped source; references are not evidence that our code passed a test.

- Spring Boot externalized configuration: https://docs.spring.io/spring-boot/reference/features/external-config.html
- Spring Boot MockMvc support: https://docs.spring.io/spring-boot/4.1/api/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.html
- PostgreSQL 18 SELECT / locking: https://www.postgresql.org/docs/18/sql-select.html
- MongoDB single-document write atomicity: https://www.mongodb.com/docs/manual/core/write-operations-atomicity/
- MongoDB embedding guidance: https://www.mongodb.com/docs/manual/data-modeling/concepts/embedding-vs-references/
- Express production security guidance: https://expressjs.com/en/advanced/best-practice-security.html
- Flutter testing overview: https://docs.flutter.dev/testing/overview
- Flutter web setup: https://docs.flutter.dev/platform-integration/web/setup
- Node crypto (scrypt, randomBytes, timingSafeEqual): https://nodejs.org/api/crypto.html

No external service, paid account or AI API is required for the implemented workflows. Dependency downloads are still needed on a machine without the existing Maven/npm/Flutter caches.
