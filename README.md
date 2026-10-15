# CarePoint — Hospital Patient Registration System

A responsive academic mini project using HTML, CSS, JavaScript, Java 17, JDBC, and SQL.

## Features
- Register patients and generate an automatic ID
- List and search by patient ID, name, or phone
- Edit and delete patient records
- Server-side validation and prepared SQL statements
- Local development uses SQLite; hosted deployment can use Supabase PostgreSQL

## Run locally (Windows)
1. Install JDK 17+ and Maven.
2. Open this folder in a terminal.
3. Run `mvn clean compile exec:java`.
4. Open http://localhost:8080.

Without `DATABASE_URL`, local development creates `hospital_patients.db` in the project folder.

## Deploy to Render with Supabase PostgreSQL
1. Create a Supabase project.
2. In Supabase choose Connect / Database and copy the PostgreSQL **Session Pooler** connection string (URI format). Session Pooler is generally the safer choice for hosts that use IPv4.
3. Push the contents of this folder to a GitHub repository. Ensure `Dockerfile`, `render.yaml`, `pom.xml`, and `src/` are at repository root.
4. In Render choose **New + → Blueprint**, connect the repository, and deploy. The Blueprint asks for `DATABASE_URL`.
5. Set `DATABASE_URL` to the complete Supabase PostgreSQL connection URI, for example the format below (use your own values; this is not a working credential):

   `postgresql://postgres.PROJECT_REF:YOUR_DATABASE_PASSWORD@aws-0-REGION.pooler.supabase.com:5432/postgres`

6. Save and deploy. In Render logs look for `Database mode: Supabase/PostgreSQL`.
7. Register a fictional test patient, refresh, and confirm the record remains.

### Environment variable
- **Key:** `DATABASE_URL`
- **Value:** Full Supabase PostgreSQL Session Pooler URI copied from your Supabase project. Do not use the Supabase anon key or service-role key here. Keep the URI secret.

The Java backend automatically creates the `patients` table on startup. No manual SQL table creation is needed. The project does not configure a Render persistent disk, so there is no disk add-on required.

## API endpoints
- `GET /api/patients?q=term` — list/search records
- `POST /api/patients` — create a patient
- `PUT /api/patients/{id}` — update a patient
- `DELETE /api/patients/{id}` — delete a patient

**Academic demo only:** no login/authentication is included. Use fictional sample data only, never real patient records.
