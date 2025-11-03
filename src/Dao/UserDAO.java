package Dao;

import java.sql.*;

public class UserDAO {

    private static Connection getConn(String name) {
        try {
            if ("Server1".equals(name)) return DatabaseConnectionServer1.getConnection();
            if ("Server2".equals(name)) return DatabaseConnectionServer2.getConnection();
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    public static boolean login(String username, String password, String server) {
        try (Connection conn = getConn(server)) {
            if (conn == null) return false;
            PreparedStatement ps = conn.prepareStatement("SELECT password, is_logged_in FROM users WHERE username = ?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return false;
            if (!rs.getString("password").equals(password)) return false;
            if (rs.getInt("is_logged_in") == 1) return false; // đang đăng nhập nơi khác
            ps.close();

            ps = conn.prepareStatement("UPDATE users SET is_logged_in = 1 WHERE username = ?");
            ps.setString(1, username);
            ps.executeUpdate();
            ps.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void logout(String username) {
        try (Connection c1 = DatabaseConnectionServer1.getConnection();
             Connection c2 = DatabaseConnectionServer2.getConnection()) {
            PreparedStatement ps1 = c1.prepareStatement("UPDATE users SET is_logged_in = 0 WHERE username = ?");
            ps1.setString(1, username);
            ps1.executeUpdate();
            ps1.close();

            PreparedStatement ps2 = c2.prepareStatement("UPDATE users SET is_logged_in = 0 WHERE username = ?");
            ps2.setString(1, username);
            ps2.executeUpdate();
            ps2.close();
        } catch (Exception ignored) {}
    }

    public static double getBalance(String username) {
        try (Connection conn = DatabaseConnectionServer1.getConnection()) {
            PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE username=?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getDouble("balance");
        } catch (Exception ignored) {}
        return 0;
    }

    public static void updateBalance(String username, double balance) {
        try (Connection c1 = DatabaseConnectionServer1.getConnection();
             Connection c2 = DatabaseConnectionServer2.getConnection()) {
            PreparedStatement ps1 = c1.prepareStatement("UPDATE users SET balance=? WHERE username=?");
            ps1.setDouble(1, balance);
            ps1.setString(2, username);
            ps1.executeUpdate();
            ps1.close();

            PreparedStatement ps2 = c2.prepareStatement("UPDATE users SET balance=? WHERE username=?");
            ps2.setDouble(1, balance);
            ps2.setString(2, username);
            ps2.executeUpdate();
            ps2.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static boolean register(String username, String password) {
        try (Connection c1 = DatabaseConnectionServer1.getConnection();
             Connection c2 = DatabaseConnectionServer2.getConnection()) {
            PreparedStatement ps = c1.prepareStatement("SELECT * FROM users WHERE username=?");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return false;

            String sql = "INSERT INTO users (username, password, balance, is_logged_in) VALUES (?, ?, 0, 0)";
            ps = c1.prepareStatement(sql);
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
            ps.close();

            ps = c2.prepareStatement(sql);
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
            ps.close();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void transfer(String from, String to, double amt) {
        double fromBal = getBalance(from);
        double toBal = getBalance(to);
        updateBalance(from, fromBal - amt);
        updateBalance(to, toBal + amt);
    }

    public static void setLoginStatus(String username, int status) {
        try (Connection c1 = DatabaseConnectionServer1.getConnection();
             Connection c2 = DatabaseConnectionServer2.getConnection()) {

            PreparedStatement ps1 = c1.prepareStatement(
                    "UPDATE users SET is_logged_in = ? WHERE username = ?");
            ps1.setInt(1, status);
            ps1.setString(2, username);
            ps1.executeUpdate();
            ps1.close();

            PreparedStatement ps2 = c2.prepareStatement(
                    "UPDATE users SET is_logged_in = ? WHERE username = ?");
            ps2.setInt(1, status);
            ps2.setString(2, username);
            ps2.executeUpdate();
            ps2.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
