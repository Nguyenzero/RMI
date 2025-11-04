package Controller;

import Client.BankingClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class BankingController {

    @FXML private TextField txtServerIP, txtPort, txtUsername, txtPassword, txtAmount;
    @FXML private ComboBox<String> cbTargetAccount;
    @FXML private Label lblBalance, lblStatus, lblAccountName, lblAccountNumber;
    @FXML private TableView<?> tblTransactions;
    @FXML private VBox accountInfoPane; // VBox chứa lblAccountNumber và lblBalance



    private String currentUser = null;


    private BankingClient client = new BankingClient();

    @FXML
    public void initialize() {
        lblStatus.setText("💬 Chưa kết nối server.");
        accountInfoPane.setVisible(false);


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
            lblBalance.setText(balance);

            lblStatus.setText("✅ Đăng nhập thành công!");
        } else {
            lblStatus.setText("❌ Sai tài khoản hoặc mật khẩu");
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
        lblStatus.setText(client.sendCommand("DEPOSIT " + txtUsername.getText().trim() + " " + txtAmount.getText().trim()));
    }

    // 🏧 Rút tiền
    @FXML
    public void onWithdraw() {
        lblStatus.setText(client.sendCommand("WITHDRAW " + txtUsername.getText().trim() + " " + txtAmount.getText().trim()));
    }

    // 🔁 Chuyển tiền
    @FXML
    public void onTransfer() {
        if (!client.isConnected()) {
            lblStatus.setText("⚠️ Chưa kết nối!");
            return;
        }

        String user = txtUsername.getText().trim();
        String to = cbTargetAccount.getValue();
        String amount = txtAmount.getText().trim();

        String res = client.sendCommand("TRANSFER " + user + " " + to + " " + amount);

        if (res.startsWith("BAL")) {
            lblBalance.setText(res.split(" ")[1] + " ₫");
            lblStatus.setText("✅ Chuyển tiền thành công!");
        } else {
            lblStatus.setText("❌ Không đủ tiền!");
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

            // Gửi lệnh LOGOUT tới server
            client.sendCommand("LOGOUT " + user);

            lblStatus.setText("✅ Đã đăng xuất!");
            accountInfoPane.setVisible(false);

            // Không xoá username trước khi gửi logout — phải gửi xong mới xoá
            txtPassword.clear();

        } catch (Exception e) {
            lblStatus.setText("❌ Lỗi khi đăng xuất!");
        }
    }

}