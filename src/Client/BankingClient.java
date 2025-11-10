package Client;

import java.io.*;
import java.net.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

public class BankingClient {
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private boolean connected = false;

    private Consumer<String> onServerMessage; // callback xử lý push
    private final BlockingQueue<String> responseQueue = new LinkedBlockingQueue<>();

    public void setOnServerMessage(Consumer<String> listener) {
        this.onServerMessage = listener;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean connect(String serverIP, int port) {
        try {
            if (connected) return true;

            socket = new Socket(serverIP, port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            // thread lắng nghe server push
            new Thread(this::listenFromServer).start();
            return true;
        } catch (IOException e) {
            connected = false;
            return false;
        }
    }

    private void listenFromServer() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                // server gửi UPDATE_BAL -> gọi callback
                if (line.startsWith("UPDATE_BAL")) {
                    if (onServerMessage != null) {
                        onServerMessage.accept(line);
                    }
                } else {
                    // response lệnh -> thêm vào queue
                    responseQueue.offer(line);
                }
            }
        } catch (IOException e) {
            connected = false;
        }
    }

    // Gửi lệnh và chờ response
    public synchronized String sendCommand(String cmd) {
        try {
            if (!connected) return "⚠️ Chưa kết nối server!";
            out.println(cmd);

            // chờ response từ queue
            String response = responseQueue.take(); // blocking nhưng chỉ lấy response
            return response;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "❌ Lỗi khi nhận dữ liệu!";
        }
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        connected = false;
    }
}
