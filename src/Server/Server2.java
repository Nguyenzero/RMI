package Server;

import Dao.UserDAO;
import java.io.*;
import java.net.*;
import java.util.*;

public class Server2 {
    private static final int PORT = 5001;
    private static final int SYNC_PORT = 12346;
    private static final String SYNC_SERVER_IP = "192.168.1.101";
    private static final int SYNC_SERVER_PORT = 12345;

    public static void main(String[] args) {
        try {
            InetAddress wifiIP = getWifiIPv4Address();
            if (wifiIP == null) {
                System.out.println("❌ Không tìm thấy địa chỉ IPv4 Wi-Fi!");
                return;
            }

            try (ServerSocket serverSocket = new ServerSocket(PORT, 50, wifiIP)) {
                System.out.println("✅ Server2 chạy tại: " + wifiIP.getHostAddress() + ":" + PORT);

                new Thread(Server2::listenSyncFromServer1).start();

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

            while (true) {
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
                        if (UserDAO.isLoggedIn(p[1])) {
                            out.println("FAIL_BUSY");
                            break;
                        }
                        if (UserDAO.login(p[1], p[2], "Server2")) {
                            double balance = UserDAO.getBalance(p[1], "Server2");
                            out.println("SUCCESS " + p[1] + " " + balance);
                            syncToServer("LOGIN " + p[1]);
                        } else out.println("FAIL");
                    }
                    case "DEPOSIT" -> {
                        double amt = Double.parseDouble(p[2]);
                        double newBal = UserDAO.getBalance(p[1]) + amt;
                        UserDAO.updateBalance(p[1], newBal);
                        out.println("BAL " + newBal);
                        syncToServer("UPDATE " + p[1] + " " + newBal);
                    }
                    case "WITHDRAW" -> {
                        double amt = Double.parseDouble(p[2]);
                        double bal = UserDAO.getBalance(p[1]);
                        if (bal < amt) {
                            out.println("FAIL_FUNDS");
                            return;
                        }
                        double newBal = bal - amt;
                        UserDAO.updateBalance(p[1], newBal);
                        out.println("BAL " + newBal);
                        syncToServer("UPDATE " + p[1] + " " + newBal);
                    }
                    case "TRANSFER" -> {
                        double amt = Double.parseDouble(p[3]);

                        if (!UserDAO.exists(p[2])) {
                            out.println("FAIL_RECEIVER");
                            break;
                        }

                        // Lấy balance người gửi trên Server2
                        double bal = UserDAO.getBalance(p[1], "Server2");
                        if (bal < amt) {
                            out.println("FAIL_FUNDS");
                            break;
                        }

                        // Trừ tiền người gửi
                        double newBalSender = bal - amt;
                        UserDAO.updateBalance(p[1], newBalSender);

                        // Cộng tiền người nhận
                        double balReceiver = UserDAO.getBalance(p[2], "Server2");
                        double newBalReceiver = balReceiver + amt;
                        UserDAO.updateBalance(p[2], newBalReceiver);

                        out.println("BAL " + newBalSender);

                        // Đồng bộ sang Server1
                        syncToServer("UPDATE " + p[1] + " " + newBalSender);
                        syncToServer("UPDATE " + p[2] + " " + newBalReceiver);
                    }


                    case "LOGOUT" -> {
                        UserDAO.setLoginStatus(p[1], 0); // reset local
                        out.println("OK");
                        syncToServer("LOGOUT " + p[1]); // sync cho server kia
                    }

                    case "LIST_USERS" -> {
                        StringBuilder sb = new StringBuilder("USERS ");
                        List<String> allUsers = UserDAO.getAllUsers(); // cần viết thêm trong UserDAO
                        for (String u : allUsers) {
                            sb.append(u).append(",");
                        }
                        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
                        out.println(sb.toString());
                    }


                    default -> out.println("INVALID");
                }

            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void syncToServer(String msg) {
        try (Socket s = new Socket(SYNC_SERVER_IP, SYNC_SERVER_PORT);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {
            out.println(msg);
        } catch (IOException ignored) {}
    }

    private static void listenSyncFromServer1() {
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