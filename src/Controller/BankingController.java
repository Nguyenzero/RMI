package Controller;

import Client.BankingClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Locale;

public class BankingController {

    @FXML private TextField txtServerIP, txtPort, txtUsername, txtPassword, txtAmount;
    @FXML private ComboBox<String> cbTargetAccount;
    @FXML private Label lblBalance, lblStatus, lblAccountName, lblAccountNumber;
    @FXML private TableView<?> tblTransactions;
    @FXML private VBox accountInfoPane; // VBox chứa lblAccountNumber và lblBalance

    private String currentUser = null;
    private BankingClient client = new BankingClient();
    private final NumberFormat vndFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));

    @FXML
    public void initialize() {
        lblStatus.setText("💬 Chưa kết nối server.");
        accountInfoPane.setVisible(false);

        // 🎯 Tự động định dạng VND khi nhập số tiền
        txtAmount.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.isEmpty()) return;

            // Xóa mọi ký tự không phải số
            String numeric = newVal.replaceAll("[^\\d]", "");

            if (numeric.isEmpty()) {
                txtAmount.clear();
                return;
            }

            try {
                double amount = Double.parseDouble(numeric);
                txtAmount.setText(vndFormat.format(amount));
                txtAmount.positionCaret(txtAmount.getText().length());
            } catch (NumberFormatException ignored) {}
        });

        Platform.runLater(() -> {
            Stage stage = (Stage) lblStatus.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                String user = txtUsername.getText().trim();
                if (!user.isEmpty()) {
                    client.sendCommand("LOGOUT " + user);
                }
            });
        });
    }

    // ⚙️ Kết nối tới server
    @FXML
    public void onConnectServer() {
        String ip = txtServerIP.getText().trim();
        int port = Integer.parseInt(txtPort.getText().trim());

        if (client.connect(ip, port)) {
            lblStatus.setText("✅ Đã kết nối tới server: " + ip + ":" + port);
        } else {
            lblStatus.setText("❌ Không thể kết nối server!");
        }
    }

    // ❌ Hủy kết nối
    @FXML
    public void onDisconnectServer() {
        client.disconnect();
        lblStatus.setText("🔌 Đã ngắt kết nối server!");
    }

    // 🔑 Đăng nhập
    @FXML
    public void onLogin() {
        if (!client.isConnected()) {
            lblStatus.setText("⚠️ Chưa kết nối server");
            return;
        }

        String username = txtUsername.getText();
        String password = txtPassword.getText();

        String response = client.sendCommand("LOGIN " + username + " " + password);

        if (response.equals("FAIL_BUSY")) {
            lblStatus.setText("⚠️ Tài khoản đang đăng nhập ở nơi khác!");
            return;
        }

        if (response.startsWith("SUCCESS")) {
            currentUser = username;
            String balance = response.split(" ")[2];

            accountInfoPane.setVisible(true);
            lblAccountNumber.setText(username);
            lblBalance.setText(formatVND(Double.parseDouble(balance)));

            lblStatus.setText("✅ Đăng nhập thành công!");
            loadTargetAccounts();
        } else {
            lblStatus.setText("❌ Sai tài khoản hoặc mật khẩu");
        }
    }

    @FXML
    public void loadTargetAccounts() {
        if (!client.isConnected()) return;

        String response = client.sendCommand("LIST_USERS");
        if (response.startsWith("USERS")) {
            String usersStr = response.substring(6);
            String[] users = usersStr.split(",");
            cbTargetAccount.getItems().clear();
            for (String u : users) {
                if (!u.equals(currentUser)) cbTargetAccount.getItems().add(u);
            }
        }
    }

    // 🧾 Đăng ký
    @FXML
    public void onRegister() {
        if (!client.isConnected()) {
            lblStatus.setText("⚠️ Chưa kết nối server!");
            return;
        }

        String user = txtUsername.getText().trim();
        String pass = txtPassword.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            lblStatus.setText("⚠️ Vui lòng nhập thông tin đăng ký!");
            return;
        }

        lblStatus.setText(client.sendCommand("REGISTER " + user + " " + pass));
    }

    // 💰 Nạp tiền
    @FXML
    public void onDeposit() {
        handleTransaction("DEPOSIT");
    }

    @FXML
    public void onWithdraw() {
        handleTransaction("WITHDRAW");
    }

    @FXML
    public void onTransfer() {
        if (currentUser == null) {
            lblStatus.setText("⚠️ Vui lòng đăng nhập trước.");
            return;
        }

        String target = cbTargetAccount.getValue();
        if (target == null || target.isEmpty()) {
            lblStatus.setText("⚠️ Vui lòng chọn tài khoản nhận!");
            return;
        }

        double amount = parseVND(txtAmount.getText());
        if (amount <= 0) {
            lblStatus.setText("⚠️ Số tiền không hợp lệ!");
            return;
        }

        String response = client.sendCommand("TRANSFER " + currentUser + " " + target + " " + amount);

        if (response.equals("FAIL_FUNDS")) {
            lblStatus.setText("❌ Số dư không đủ để chuyển!");
        } else if (response.equals("FAIL_RECEIVER")) {
            lblStatus.setText("❌ Tài khoản nhận không tồn tại!");
        } else if (response.startsWith("BAL")) {
            double newBal = Double.parseDouble(response.split(" ")[1]);
            lblBalance.setText(formatVND(newBal));
            lblStatus.setText("✅ Chuyển tiền thành công!");
        } else {
            lblStatus.setText("❌ Lỗi khi chuyển tiền!");
        }
    }

    private void handleTransaction(String type) {
        if (currentUser == null) {
            lblStatus.setText("⚠️ Vui lòng đăng nhập trước.");
            return;
        }

        double amount = parseVND(txtAmount.getText());
        if (amount <= 0) {
            lblStatus.setText("⚠️ Số tiền không hợp lệ!");
            return;
        }

        String response = client.sendCommand(type + " " + currentUser + " " + amount);

        if (response.equals("FAIL_FUNDS")) {
            lblStatus.setText("❌ Số dư không đủ!");
        } else if (response.startsWith("BAL")) {
            double newBal = Double.parseDouble(response.split(" ")[1]);
            lblBalance.setText(formatVND(newBal));
            lblStatus.setText(type.equals("DEPOSIT") ? "✅ Nạp tiền thành công!" : "✅ Rút tiền thành công!");
        } else {
            lblStatus.setText("❌ Lỗi khi thực hiện giao dịch!");
        }
    }

    // 🚪 Đăng xuất
    @FXML
    public void onLogout() {
        try {
            String user = txtUsername.getText().trim();

            if (user.isEmpty()) {
                lblStatus.setText("⚠️ Bạn chưa đăng nhập!");
                return;
            }

            client.sendCommand("LOGOUT " + user);

            lblStatus.setText("✅ Đã đăng xuất!");
            accountInfoPane.setVisible(false);
            txtPassword.clear();

        } catch (Exception e) {
            lblStatus.setText("❌ Lỗi khi đăng xuất!");
        }
    }

    private String formatVND(double amount) {
        return vndFormat.format(amount);
    }

    private double parseVND(String text) {
        try {
            if (text == null || text.isEmpty()) return 0;
            return vndFormat.parse(text).doubleValue();
        } catch (ParseException e) {
            try {
                return Double.parseDouble(text.replaceAll("[^\\d.]", ""));
            } catch (Exception ex) {
                return 0;
            }
        }
    }
}
