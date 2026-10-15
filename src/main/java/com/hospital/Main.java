package com.hospital;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/**
 * MAIN CLASS: Java backend of the Hospital Patient Registration System.
 * It starts the website, receives form requests, and saves patient data in SQLite.
 * The screen itself is made with HTML, CSS and JavaScript.
 */
public class Main {
    private static final Gson gson = new Gson();
    // Render: use DATABASE_URL (PostgreSQL). Local development: use SQLite.
    private static final String DATABASE_URL = System.getenv("DATABASE_URL");
    private static final boolean USE_POSTGRES = DATABASE_URL != null && !DATABASE_URL.isBlank();
    private static final String SQLITE_URL = "jdbc:sqlite:" + System.getenv().getOrDefault("DATABASE_PATH", "hospital_patients.db");
    private static final Path WEB = Path.of("src/main/resources/static");

    // Program starts here. Prepare the database, then start the local website server.
    public static void main(String[] args) throws Exception {
        initDb();
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        server.createContext("/api/patients", Main::handlePatients);
        server.createContext("/", Main::handleStatic);
        server.setExecutor(null);
        server.start();
        System.out.println("Hospital Patient Registration System running on port " + port);
        System.out.println("Database mode: " + (USE_POSTGRES ? "Supabase/PostgreSQL" : "local SQLite"));
        System.out.println("Press Ctrl+C to stop.");
    }

    // Opens a connection to the SQLite database file.
    private static Connection db() throws SQLException {
        if (!USE_POSTGRES) return DriverManager.getConnection(SQLITE_URL);
        try {
            // Render's DATABASE_URL commonly uses postgres:// or postgresql://.
            URI uri = URI.create(DATABASE_URL.replaceFirst("^postgres://", "postgresql://"));
            String userInfo = uri.getUserInfo();
            if (userInfo == null || !userInfo.contains(":"))
                throw new SQLException("DATABASE_URL must include PostgreSQL username and password.");
            String username = URLDecoder.decode(userInfo.substring(0, userInfo.indexOf(':')), StandardCharsets.UTF_8);
            String password = URLDecoder.decode(userInfo.substring(userInfo.indexOf(':') + 1), StandardCharsets.UTF_8);
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "")
                    + uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            Properties props = new Properties();
            props.setProperty("user", username);
            props.setProperty("password", password);
            props.setProperty("sslmode", "require");
            return DriverManager.getConnection(jdbcUrl, props);
        } catch (IllegalArgumentException e) {
            throw new SQLException("Invalid DATABASE_URL format. Paste the Supabase PostgreSQL connection string into Render Environment.", e);
        }
    }

    // Creates the patients table only if it does not already exist.
    private static void initDb() throws SQLException {
        String sql = USE_POSTGRES ? """
            CREATE TABLE IF NOT EXISTS patients (
              id BIGSERIAL PRIMARY KEY,
              full_name TEXT NOT NULL,
              age INTEGER NOT NULL CHECK(age BETWEEN 0 AND 120),
              gender TEXT NOT NULL,
              phone TEXT NOT NULL,
              address TEXT NOT NULL,
              blood_group TEXT NOT NULL,
              symptoms TEXT NOT NULL,
              registered_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
            """ : """
            CREATE TABLE IF NOT EXISTS patients (
              id INTEGER PRIMARY KEY AUTOINCREMENT,
              full_name TEXT NOT NULL,
              age INTEGER NOT NULL CHECK(age BETWEEN 0 AND 120),
              gender TEXT NOT NULL,
              phone TEXT NOT NULL,
              address TEXT NOT NULL,
              blood_group TEXT NOT NULL,
              symptoms TEXT NOT NULL,
              registered_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
            )
            """;
        try (Connection c = db(); Statement s = c.createStatement()) { s.execute(sql); }
    }

    // Handles patient actions requested by the website: list, add, edit, and delete.
    private static void handlePatients(HttpExchange ex) throws IOException {
        addCorsHeaders(ex);
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();
            if ("OPTIONS".equals(method)) { send(ex, 204, ""); return; }
            if ("/api/patients".equals(path) && "GET".equals(method)) {
                String q = queryParam(ex.getRequestURI().getRawQuery(), "q");
                sendJson(ex, 200, listPatients(q == null ? "" : q));
                return;
            }
            if ("/api/patients".equals(path) && "POST".equals(method)) {
                Map<?,?> body = gson.fromJson(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), Map.class);
                PatientInput p = validate(body);
                String sql = "INSERT INTO patients(full_name,age,gender,phone,address,blood_group,symptoms) VALUES(?,?,?,?,?,?,?)";
                try (Connection c=db(); PreparedStatement ps=c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    bind(ps,p); ps.executeUpdate();
                    int id;
                    try (ResultSet rs=ps.getGeneratedKeys()) { id=rs.next()?rs.getInt(1):-1; }
                    sendJson(ex, 201, Map.of("message","Patient registered successfully","id",id));
                }
                return;
            }
            String[] bits = path.split("/");
            if (bits.length == 4 && "api".equals(bits[1]) && "patients".equals(bits[2])) {
                int id;
                try { id=Integer.parseInt(bits[3]); } catch (NumberFormatException e) { sendJson(ex,400,Map.of("error","Invalid patient ID")); return; }
                if ("PUT".equals(method)) {
                    Map<?,?> body=gson.fromJson(new String(ex.getRequestBody().readAllBytes(),StandardCharsets.UTF_8),Map.class);
                    PatientInput p=validate(body);
                    String sql="UPDATE patients SET full_name=?,age=?,gender=?,phone=?,address=?,blood_group=?,symptoms=? WHERE id=?";
                    try(Connection c=db();PreparedStatement ps=c.prepareStatement(sql)){
                        bind(ps,p); ps.setInt(8,id);
                        int changed=ps.executeUpdate();
                        if(changed==0){sendJson(ex,404,Map.of("error","Patient not found"));return;}
                    }
                    sendJson(ex,200,Map.of("message","Patient updated successfully")); return;
                }
                if ("DELETE".equals(method)) {
                    try(Connection c=db();PreparedStatement ps=c.prepareStatement("DELETE FROM patients WHERE id=?")){
                        ps.setInt(1,id);
                        if(ps.executeUpdate()==0){sendJson(ex,404,Map.of("error","Patient not found"));return;}
                    }
                    sendJson(ex,200,Map.of("message","Patient deleted successfully")); return;
                }
            }
            sendJson(ex,405,Map.of("error","Method or endpoint not supported"));
        } catch (IllegalArgumentException e) {
            sendJson(ex,400,Map.of("error",e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            sendJson(ex,500,Map.of("error","Server error. Check the console for details."));
        }
    }

    // Reads patient rows from the database. Search can match ID, name, or phone.
    private static List<Map<String,Object>> listPatients(String q) throws SQLException {
        List<Map<String,Object>> out=new ArrayList<>();
        String pattern="%"+q.trim()+"%";
        String sql="SELECT * FROM patients WHERE CAST(id AS TEXT) LIKE ? OR full_name LIKE ? OR phone LIKE ? ORDER BY id DESC";
        try(Connection c=db();PreparedStatement ps=c.prepareStatement(sql)){
            ps.setString(1,pattern);ps.setString(2,pattern);ps.setString(3,pattern);
            try(ResultSet rs=ps.executeQuery()){
                while(rs.next()){
                    Map<String,Object> row=new LinkedHashMap<>();
                    row.put("id",rs.getInt("id")); row.put("fullName",rs.getString("full_name"));
                    row.put("age",rs.getInt("age")); row.put("gender",rs.getString("gender"));
                    row.put("phone",rs.getString("phone")); row.put("address",rs.getString("address"));
                    row.put("bloodGroup",rs.getString("blood_group")); row.put("symptoms",rs.getString("symptoms"));
                    row.put("registeredAt",rs.getString("registered_at")); out.add(row);
                }
            }
        }
        return out;
    }

    // Checks the form values before saving them, so invalid data is rejected.
    private static PatientInput validate(Map<?,?> m) {
        String name=str(m,"fullName"), gender=str(m,"gender"), phone=str(m,"phone"),
            address=str(m,"address"), blood=str(m,"bloodGroup"), symptoms=str(m,"symptoms");
        Object ageValue=m.get("age");
        if(name.isBlank()||gender.isBlank()||phone.isBlank()||address.isBlank()||blood.isBlank()||symptoms.isBlank()||ageValue==null)
            throw new IllegalArgumentException("Please fill in all required fields.");
        int age;
        try { age=(int)Double.parseDouble(ageValue.toString()); } catch(Exception e) { throw new IllegalArgumentException("Age must be a whole number."); }
        if(age<0||age>120) throw new IllegalArgumentException("Age must be between 0 and 120.");
        if(!phone.matches("[0-9+() -]{7,20}")||phone.replaceAll("\\D","").length()<7)
            throw new IllegalArgumentException("Enter a valid phone number with at least 7 digits.");
        if(!Set.of("Female","Male","Other").contains(gender)) throw new IllegalArgumentException("Choose a valid gender.");
        if(!Set.of("A+","A-","B+","B-","AB+","AB-","O+","O-","Unknown").contains(blood)) throw new IllegalArgumentException("Choose a valid blood group.");
        return new PatientInput(name,age,gender,phone,address,blood,symptoms);
    }
    private static String str(Map<?,?> m,String k){Object v=m.get(k);return v==null?"":v.toString().trim();}
    // Places the patient values into the SQL statement safely using placeholders.
    private static void bind(PreparedStatement ps,PatientInput p)throws SQLException{
        ps.setString(1,p.name);ps.setInt(2,p.age);ps.setString(3,p.gender);ps.setString(4,p.phone);
        ps.setString(5,p.address);ps.setString(6,p.blood);ps.setString(7,p.symptoms);
    }
    private record PatientInput(String name,int age,String gender,String phone,String address,String blood,String symptoms){}

    // Sends the HTML, CSS, and JavaScript files to the browser.
    private static void handleStatic(HttpExchange ex)throws IOException{
        if(!"GET".equals(ex.getRequestMethod())){send(ex,405,"Method not allowed");return;}
        String requested=ex.getRequestURI().getPath();
        if("/".equals(requested)) requested="/index.html";
        Path file=WEB.resolve(requested.substring(1)).normalize();
        if(!file.startsWith(WEB.normalize())||!Files.exists(file)||Files.isDirectory(file)){send(ex,404,"Not found");return;}
        String type=requested.endsWith(".css")?"text/css; charset=utf-8":requested.endsWith(".js")?"application/javascript; charset=utf-8":"text/html; charset=utf-8";
        byte[] bytes=Files.readAllBytes(file);ex.getResponseHeaders().set("Content-Type",type);
        ex.sendResponseHeaders(200,bytes.length);try(var os=ex.getResponseBody()){os.write(bytes);}
    }
    private static String queryParam(String query,String key){
        if(query==null)return null;
        for(String part:query.split("&")){String[] pair=part.split("=",2);if(pair.length==2&&pair[0].equals(key))return java.net.URLDecoder.decode(pair[1],StandardCharsets.UTF_8);}
        return null;
    }
    // Converts Java data to JSON so JavaScript can read the response.
    private static void addCorsHeaders(HttpExchange ex) {
        String origin = System.getenv().getOrDefault("FRONTEND_ORIGIN", "*").trim();
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", origin.isEmpty() ? "*" : origin);
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ex.getResponseHeaders().set("Access-Control-Max-Age", "86400");
    }

    private static void sendJson(HttpExchange ex,int status,Object obj)throws IOException{
        addCorsHeaders(ex);
        ex.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");
        send(ex,status,gson.toJson(obj));
    }
    private static void send(HttpExchange ex,int status,String body)throws IOException{
        addCorsHeaders(ex);
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status,status==204?-1:bytes.length);
        if(status!=204)try(var os=ex.getResponseBody()){os.write(bytes);}
    }
}