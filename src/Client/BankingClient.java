
package Client;

import java.io.*;
import java.net.*;

public class BankingClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;

    public boolean isConnected() {
        return connected;
    }

    public boolean connect(String serverIP, int port) {
        try {
            if (connected) {
                System.out.println("✅ Đã kết nối trước đó, không cần kết nối lại!");
                return true;
            }

            socket = new Socket(serverIP, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("✅ Kết nối thành công tới " + serverIP + ":" + port);
            return true;
        } catch (IOException e) {
            System.out.println("❌ Không thể kết nối: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    public String sendCommand(String cmd) {
        try {
            if (!connected) return "⚠️ Chưa kết nối server!";
            out.println(cmd);
            return in.readLine();
        } catch (IOException e) {
            return "❌ Lỗi gửi dữ liệu!";
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}

        connected = false;
        socket = null;
        in = null;
        out = null;

        System.out.println("🔌 Đã ngắt kết nối server.");
    }
}
