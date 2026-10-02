# Checkpoint 4A - Auth Compose Multiplatform UI

## Hành vi Android trước migration

Ba route Android nhận chung `AuthViewModel` và callback điều hướng. Mỗi màn hình tự giữ dữ liệu nhập bằng `remember`, đọc `AuthState`, gọi trực tiếp hàm submit trên ViewModel và hiển thị lỗi/thành công inline. Login còn quan sát đồng thời `isLoggedIn` và `AuthState.isSuccess`, nên có hai nguồn có thể kích hoạt cùng callback thành công.

- Login: email, mật khẩu, ẩn/hiện mật khẩu, link Quên mật khẩu, link Đăng ký, loading và lỗi.
- Register: họ tên, email, mật khẩu luôn bị che, link Đăng nhập, loading và lỗi.
- Forgot Password: email, mô tả Firebase reset-email, loading, thông báo thành công màu xanh hoặc lỗi màu đỏ và link quay lại Login.
- Navigation: Login sang Home/Register/Forgot Password; Register sang Home/Login; Forgot Password trở về Login.
- Giao diện: ảnh nền phủ đầy, lớp đen alpha `0.6`, padding `24dp`, logo `60dp`, màu nhấn `#FFD700`, trường nhập bo `12dp`, nút cao `50dp`.

## Hành vi sau migration

`AuthUiPresenter` trong `commonMain` sở hữu dữ liệu nhập, password visibility, validation/use-case submission, loading, lỗi và event một lần. Presenter chặn submit mới đồng bộ ngay khi một request đang hoạt động. `AuthUiEventEnvelope` cấp id tăng dần; Android chỉ điều hướng rồi consume đúng id đó.

Ba composable `LoginScreenContent`, `RegisterScreenContent` và `ForgotPasswordScreenContent` cùng các component auth nằm trong `commonMain`. Chúng chỉ nhận `AuthUiState` và callback Kotlin, không nhận ViewModel, navigation controller, Firebase hay kiểu Android.

Ba hàm route Android giữ nguyên chữ ký cũ. Chúng dùng `collectAsStateWithLifecycle`, chuyển callback sang presenter qua `AuthViewModel` facade và xử lý event điều hướng một lần. Navigation graph không thay đổi.

## Resource và giới hạn nền tảng

Chuỗi auth, ảnh nền và logo auth đã có trong Compose Multiplatform Resources. Bản `logo.jpg` Android vẫn được giữ vì Home, About và notification hiện còn truy cập `R.drawable.logo`; xóa bản này sẽ thay đổi code ngoài checkpoint. Bản ảnh nền Android cũ đã được bỏ sau khi không còn reference.

Windows có thể biên dịch/test shared Android target nhưng không thể chạy `iosSimulatorArm64Test`. Cần macOS/Xcode để build framework iOS, kiểm tra resource packaging, render ba màn hình trên thiết bị/simulator và chạy UI/integration test iOS.
