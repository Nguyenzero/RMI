    package Dao;

    import java.sql.*;
    import java.util.ArrayList;
    import java.util.List;

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

        public static boolean transfer(String from, String to, double amt) {
            try (Connection c1 = DatabaseConnectionServer1.getConnection();
                 Connection c2 = DatabaseConnectionServer2.getConnection()) {

                // Kiểm tra từ server1
                PreparedStatement psCheckFrom = c1.prepareStatement("SELECT balance FROM users WHERE username=?");
                psCheckFrom.setString(1, from);
                ResultSet rsFrom = psCheckFrom.executeQuery();
                if (!rsFrom.next()) return false; // người gửi không tồn tại
                double fromBal = rsFrom.getDouble("balance");
                if (fromBal < amt) return false; // không đủ tiền

                PreparedStatement psCheckTo = c1.prepareStatement("SELECT balance FROM users WHERE username=?");
                psCheckTo.setString(1, to);
                ResultSet rsTo = psCheckTo.executeQuery();
                if (!rsTo.next()) return false; // người nhận không tồn tại

                // Transaction atomic trên server1
                c1.setAutoCommit(false);
                PreparedStatement psFrom = c1.prepareStatement("UPDATE users SET balance=? WHERE username=?");
                psFrom.setDouble(1, fromBal - amt);
                psFrom.setString(2, from);
                psFrom.executeUpdate();

                double toBal = rsTo.getDouble("balance");
                PreparedStatement psTo = c1.prepareStatement("UPDATE users SET balance=? WHERE username=?");
                psTo.setDouble(1, toBal + amt);
                psTo.setString(2, to);
                psTo.executeUpdate();
                c1.commit();
                c1.setAutoCommit(true);

                // Đồng bộ sang server2
                updateBalance(to, toBal + amt);
                updateBalance(from, fromBal - amt);

                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        public static void setLoginStatus(String username, int status) {
            try (Connection c1 = DatabaseConnectionServer1.getConnection();
                 Connection c2 = DatabaseConnectionServer2.getConnection()) {

                PreparedStatement ps1 = c1.prepareStatement("UPDATE users SET is_logged_in=? WHERE username=?");
                ps1.setInt(1, status);
                ps1.setString(2, username);
                ps1.executeUpdate();
                ps1.close();

                PreparedStatement ps2 = c2.prepareStatement("UPDATE users SET is_logged_in=? WHERE username=?");
                ps2.setInt(1, status);
                ps2.setString(2, username);
                ps2.executeUpdate();
                ps2.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static void logout(String username) {
            setLoginStatus(username, 0);
        }


        public static boolean isLoggedIn(String username) {
            try (Connection c1 = DatabaseConnectionServer1.getConnection();
                 Connection c2 = DatabaseConnectionServer2.getConnection()) {

                PreparedStatement ps1 = c1.prepareStatement("SELECT is_logged_in FROM users WHERE username=?");
                ps1.setString(1, username);
                ResultSet rs1 = ps1.executeQuery();
                if (rs1.next() && rs1.getInt("is_logged_in") == 1) return true;

                PreparedStatement ps2 = c2.prepareStatement("SELECT is_logged_in FROM users WHERE username=?");
                ps2.setString(1, username);
                ResultSet rs2 = ps2.executeQuery();
                if (rs2.next() && rs2.getInt("is_logged_in") == 1) return true;

            } catch (Exception ignored) {}

            return false;
        }


        // Lấy balance từ server tương ứng
        public static double getBalance(String username, String server) {
            try (Connection conn = "Server1".equals(server) ?
                    DatabaseConnectionServer1.getConnection() :
                    DatabaseConnectionServer2.getConnection()
            ) {
                PreparedStatement ps = conn.prepareStatement("SELECT balance FROM users WHERE username=?");
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) return rs.getDouble("balance");
            } catch (Exception ignored) {}
            return 0;
        }

        public static boolean exists(String username) {
            try (Connection c1 = DatabaseConnectionServer1.getConnection()) {
                PreparedStatement ps = c1.prepareStatement("SELECT 1 FROM users WHERE username=?");
                ps.setString(1, username);
                ResultSet rs = ps.executeQuery();
                return rs.next();
            } catch (Exception e) { return false; }
        }

        public static List<String> getAllUsers() {
            List<String> list = new ArrayList<>();
            try (Connection c1 = DatabaseConnectionServer1.getConnection()) {
                PreparedStatement ps = c1.prepareStatement("SELECT username FROM users");
                ResultSet rs = ps.executeQuery();
                while (rs.next()) list.add(rs.getString("username"));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return list;
        }



    }