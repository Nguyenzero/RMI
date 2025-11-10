package Server;

import Dao.UserDAO;
import java.io.*;
import java.net.*;
import java.util.*;

public class Server1 {
    private static final int PORT = 5000;
    private static final int SYNC_PORT = 12345;
    private static final String SYNC_SERVER_IP = "192.168.1.101";
    private static final int SYNC_SERVER_PORT = 12346;
    private static final List<PrintWriter> clients = Collections.synchronizedList(new ArrayList<>());


    public static void main(String[] args) {
        try {
            InetAddress wifiIP = getWifiIPv4Address();
            if (wifiIP == null) {
                System.out.println("❌ Không tìm thấy địa chỉ IPv4 Wi-Fi!");
                return;
            }

            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, wifiIP)) {
                System.out.println("✅ Server1 chạy tại: " + wifiIP.getHostAddress() + ":" + PORT);

                // Lắng nghe sync từ Server2
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
        PrintWriter out = null;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            out = new PrintWriter(socket.getOutputStream(), true);
            clients.add(out); // 🌟 thêm client vào danh sách

            while (true) {
                String line = in.readLine();
                if (line == null) break; // client đóng -> thoát

                String[] p = line.split(" ");
                String cmd = p[0].toUpperCase();

                switch (cmd) {
                    case "REGISTER" -> {
                        if (UserDAO.register(p[1], p[2])) out.println("✅ Đăng ký thành công!");
                        else out.println("❌ Tên tài khoản đã tồn tại!");
                    }

                    case "LOGIN" -> {
                        if (UserDAO.isLoggedIn(p[1])) {
                            out.println("FAIL_BUSY");
                            break;
                        }
                        if (UserDAO.login(p[1], p[2], "Server1")) {
                            double balance = UserDAO.getBalance(p[1], "Server1");
                            out.println("SUCCESS " + p[1] + " " + balance);
                            syncToServer("LOGIN " + p[1]);
                        } else out.println("FAIL");
                    }

                    case "DEPOSIT" -> {
                        double amt = Double.parseDouble(p[2]);
                        double newBal = UserDAO.getBalance(p[1], "Server1") + amt;
                        UserDAO.updateBalance(p[1], newBal);
                        out.println("BAL " + newBal);
                        broadcastBalance(p[1], newBal); // 🌟 broadcast realtime
                        syncToServer("UPDATE " + p[1] + " " + newBal);
                    }

                    case "WITHDRAW" -> {
                        double amt = Double.parseDouble(p[2]);
                        double bal = UserDAO.getBalance(p[1], "Server1");
                        if (bal < amt) {
                            out.println("FAIL_FUNDS");
                            break;
                        }
                        double newBal = bal - amt;
                        UserDAO.updateBalance(p[1], newBal);
                        out.println("BAL " + newBal);
                        broadcastBalance(p[1], newBal); // 🌟 broadcast realtime
                        syncToServer("UPDATE " + p[1] + " " + newBal);
                    }

                    case "TRANSFER" -> {
                        double amt = Double.parseDouble(p[3]);

                        if (!UserDAO.exists(p[2])) {
                            out.println("FAIL_RECEIVER");
                            break;
                        }

                        double bal = UserDAO.getBalance(p[1], "Server1");
                        if (bal < amt) {
                            out.println("FAIL_FUNDS");
                            break;
                        }

                        // Trừ tiền người gửi
                        double newBalSender = bal - amt;
                        UserDAO.updateBalance(p[1], newBalSender);

                        // Cộng tiền người nhận
                        double balReceiver = UserDAO.getBalance(p[2], "Server1");
                        double newBalReceiver = balReceiver + amt;
                        UserDAO.updateBalance(p[2], newBalReceiver);

                        out.println("BAL " + newBalSender);

                        // 🌟 broadcast cả sender và receiver
                        broadcastBalance(p[1], newBalSender);
                        broadcastBalance(p[2], newBalReceiver);

                        // Đồng bộ sang server kia
                        syncToServer("UPDATE " + p[1] + " " + newBalSender);
                        syncToServer("UPDATE " + p[2] + " " + newBalReceiver);
                    }

                    case "LOGOUT" -> {
                        UserDAO.setLoginStatus(p[1], 0);
                        out.println("OK");
                        syncToServer("LOGOUT " + p[1]);
                    }

                    case "LIST_USERS" -> {
                        StringBuilder sb = new StringBuilder("USERS ");
                        List<String> allUsers = UserDAO.getAllUsers();
                        for (String u : allUsers) sb.append(u).append(",");
                        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
                        out.println(sb.toString());
                    }

                    default -> out.println("INVALID");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (out != null) clients.remove(out); // 🌟 xóa client khi ngắt kết nối
        }
    }

    // 🌟 Thêm phương thức broadcast
    private static void broadcastBalance(String username, double balance) {
        String msg = "UPDATE_BAL " + username + " " + balance;
        synchronized (clients) {
            for (PrintWriter pw : clients) {
                pw.println(msg);
            }
        }
    }



    private static void syncToServer(String msg) {
        try (Socket s = new Socket(SYNC_SERVER_IP, SYNC_SERVER_PORT);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println(msg);
        } catch (IOException ignored) {}
    }

    private static void listenSyncFromServer2() {
        try (ServerSocket syncSocket = new ServerSocket(SYNC_PORT)) {
            while (true) {
                Socket s = syncSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String msg = in.readLine();
                if (msg != null) handleSyncMessage(msg);
            }
        } catch (IOException ignored) {}
    }

    private static void handleSyncMessage(String msg) {
        String[] p = msg.split(" ");
        switch (p[0]) {
            case "LOGIN" -> UserDAO.setLoginStatus(p[1], 1);
            case "UPDATE" -> UserDAO.updateBalance(p[1], Double.parseDouble(p[2]));
            case "TRANSFER" -> UserDAO.transfer(p[1], p[2], Double.parseDouble(p[3]));
            case "LOGOUT" -> UserDAO.setLoginStatus(p[1], 0);
        }
    }

    private static InetAddress getWifiIPv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface ni : Collections.list(interfaces)) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                if (ni.getDisplayName().toLowerCase().contains("wi")) {
                    for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                        if (ia.getAddress() instanceof Inet4Address) return ia.getAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}