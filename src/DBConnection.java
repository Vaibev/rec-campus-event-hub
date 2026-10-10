import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Paste your Aiven details here:
    private static final String HOST = "mysql-188c3cd1-vvaibevv00-2007.f.aivencloud.com";      // e.g., mysql-xxxx.f.aivencloud.com
    private static final String PORT = "19933";      // e.g., 12450
    private static final String DB_NAME = "defaultdb";
    private static final String USER = "avnadmin";
    private static final String PASS = "AVNS_BQsDVSjde_FnhnwfAf_";  // your actual password string

    private static final String URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DB_NAME 
        + "?useSSL=true&requireSSL=true&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL Driver not found: " + e.getMessage());
        }
        return DriverManager.getConnection(URL, USER, PASS);
    }
}