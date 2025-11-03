package Server;

import Dao.UserDAO;
import java.io.*;
import java.net.*;
import java.util.*;

public class Server1 {
    private static final int PORT = 5000;
    private static final int SYNC_PORT = 12345; // Cổng nhận đồng bộ từ Server2
    private static final String SYNC_SERVER_IP = "192.168.1.101"; // ⚠️ IP Wi-Fi của Server2
    private static final int SYNC_SERVER_PORT = 12346; // ✅ Cổng mà Server2 đang lắng nghe

    public static void main(String[] args) {
        try {
            InetAddress wifiIP = getWifiIPv4Address();
            if (wifiIP == null) {
                System.out.println("❌ Không tìm thấy địa chỉ IPv4 Wi-Fi!");
                return;
            }

            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, wifiIP)) {
                System.out.println("✅ Server1 chạy tại: " + wifiIP.getHostAddress() + ":" + PORT);
                new Thread(Server1::listenSyncFromServer2).start();

                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket socket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            String line = in.readLine();
            if (line == null) return;
            String[] p = line.split(" ");
            String cmd = p[0].toUpperCase();

            switch (cmd) {
                case "REGISTER" -> {
                    if (UserDAO.register(p[1], p[2])) out.println("✅ Đăng ký thành công!");
                    else out.println("❌ Tên tài khoản đã tồn tại!");
                }
                case "LOGIN" -> {
                    if (UserDAO.login(p[1], p[2], "Server1")) {
                        out.println("✅ Đăng nhập thành công!");
                        syncToServer("LOGIN " + p[1]);
                    } else out.println("❌ Sai tài khoản hoặc mật khẩu!");
                }
                case "DEPOSIT" -> {
                    double amt = Double.parseDouble(p[2]);
                    double newBal = UserDAO.getBalance(p[1]) + amt;
                    UserDAO.updateBalance(p[1], newBal);
                    out.println("💰 Nạp thành công! Số dư mới: " + newBal);
                    syncToServer("UPDATE " + p[1] + " " + newBal);
                }
                case "WITHDRAW" -> {
                    double amt = Double.parseDouble(p[2]);
                    double newBal = UserDAO.getBalance(p[1]) - amt;
                    if (newBal < 0) {
                        out.println("❌ Số dư không đủ!");
                        return;
                    }
                    UserDAO.updateBalance(p[1], newBal);
                    out.println("🏧 Rút thành công! Số dư: " + newBal);
                    syncToServer("UPDATE " + p[1] + " " + newBal);
                }
                case "TRANSFER" -> {
                    double amt = Double.parseDouble(p[3]);
                    double fromBal = UserDAO.getBalance(p[1]);
                    if (fromBal < amt) {
                        out.println("❌ Số dư không đủ!");
                        return;
                    }
                    UserDAO.updateBalance(p[1], fromBal - amt);
                    double toBal = UserDAO.getBalance(p[2]) + amt;
                    UserDAO.updateBalance(p[2], toBal);
                    out.println("✅ Chuyển tiền thành công! Số dư còn lại: " + (fromBal - amt));
                    syncToServer("TRANSFER " + p[1] + " " + p[2] + " " + amt);
                }
                case "LOGOUT" -> {
                    UserDAO.logout(p[1]);
                    out.println("🚪 Đăng xuất thành công!");
                    syncToServer("LOGOUT " + p[1]);
                }
                default -> out.println("❓ Lệnh không hợp lệ!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void syncToServer(String msg) {
        try (Socket s = new Socket(SYNC_SERVER_IP, SYNC_SERVER_PORT);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println(msg);
            System.out.println("🔁 Đồng bộ sang Server2: " + msg);
        } catch (IOException e) {
            System.out.println("⚠️ Không thể kết nối tới Server2 (" + SYNC_SERVER_IP + ":" + SYNC_SERVER_PORT + ")");
        }
    }

    private static void listenSyncFromServer2() {
        try (ServerSocket syncSocket = new ServerSocket(SYNC_PORT)) {
            System.out.println("🔄 Server1 lắng nghe đồng bộ từ Server2 tại cổng: " + SYNC_PORT);
            while (true) {
                Socket s = syncSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String msg = in.readLine();
                if (msg != null) handleSyncMessage(msg);
                s.close();
            }
        } catch (IOException e) {
            System.out.println("⚠️ Lỗi khi lắng nghe đồng bộ: " + e.getMessage());
        }
    }

    private static void handleSyncMessage(String msg) {
        System.out.println("🔃 Nhận đồng bộ từ Server2: " + msg);
        String[] p = msg.split(" ");
        switch (p[0].toUpperCase()) {
            case "LOGIN" -> UserDAO.setLoginStatus(p[1], 1);    // ✅ sửa ở đây
            case "UPDATE" -> UserDAO.updateBalance(p[1], Double.parseDouble(p[2]));
            case "TRANSFER" -> UserDAO.transfer(p[1], p[2], Double.parseDouble(p[3]));
            case "LOGOUT" -> UserDAO.setLoginStatus(p[1], 0);   // ✅ sửa ở đây
        }
    }


    private static InetAddress getWifiIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (ni.isLoopback() || !ni.isUp() || ni.isVirtual()) continue;
                String name = ni.getDisplayName().toLowerCase();
                if (name.contains("wlan") || name.contains("wi-fi") || name.contains("wireless")) {
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        InetAddress addr = ia.getAddress();
                        if (addr instanceof Inet4Address) return addr;
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}
