package Controller;

import Client.BankingClient;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class BankingController {

    @FXML private TextField txtServerIP, txtPort, txtUsername, txtPassword, txtAmount;
    @FXML private ComboBox<String> cbTargetAccount;
    @FXML private Label lblBalance, lblStatus, lblAccountName, lblAccountNumber;
    @FXML private TableView<?> tblTransactions;

    private BankingClient client = new BankingClient();

    @FXML
    public void initialize() {
        lblStatus.setText("💬 Chưa kết nối server.");
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
            lblStatus.setText("⚠️ Chưa kết nối server!");
            return;
        }

        String user = txtUsername.getText().trim();
        String pass = txtPassword.getText().trim();

        if (user.isEmpty() || pass.isEmpty()) {
            lblStatus.setText("⚠️ Vui lòng nhập tên đăng nhập và mật khẩu!");
            return;
        }

        lblStatus.setText(client.sendCommand("LOGIN " + user + " " + pass));
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
        lblStatus.setText(client.sendCommand("TRANSFER " + txtUsername.getText().trim() + " " + cbTargetAccount.getValue() + " " + txtAmount.getText().trim()));
    }

    // 🚪 Đăng xuất
    @FXML
    public void onLogout() {
        lblStatus.setText(client.sendCommand("LOGOUT " + txtUsername.getText().trim()));
        client.disconnect();
    }
}
