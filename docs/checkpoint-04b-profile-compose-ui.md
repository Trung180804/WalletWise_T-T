# Checkpoint 4B - Profile Compose Multiplatform UI

## Hành vi Android trước migration

- `ProfileScreen` giữ `ProfileRoute`, báo `onSubScreenChange`, chặn Back ở mọi
  route con và đưa các route Settings con về Settings trước khi về Profile chính.
- Profile chính dùng padding 16dp và scroll dọc; avatar tròn hồng `#E91E63`
  kích thước 90dp hiển thị chữ cái đầu; tên 22sp đậm, email 14sp xám.
- Sáu menu theo thứ tự: Hồ sơ, Kế hoạch & Phân bổ, Quy đổi tiền tệ, Chăm sóc
  khách hàng, Cài đặt, Về chúng tôi. Mỗi hàng padding dọc 16dp, icon 24dp xám,
  chữ 16sp, mũi tên phải và divider theo theme.
- Nút Đăng xuất/Đăng nhập cao 55dp, bo 16dp. Đăng xuất mở dialog bo 24dp với
  tiêu đề hồng `#FA3B70`, nội dung xác nhận và hai nút Hủy bỏ/Đăng xuất cao
  50dp. Callback share Android được truyền vào `MainProfileView`, nhưng không
  có menu hiển thị nào gọi callback này.
- Edit Profile có header cao 60dp, avatar 56dp dùng Coil khi URL không rỗng và
  chữ cái đầu khi URL rỗng. Các hàng theo thứ tự: ID, Email, Họ và tên, Giới
  tính, Đổi mật khẩu; padding dọc 18dp và divider theo theme.
- Chạm avatar mở Android `GetContent("image/*")`; `Uri` được đọc và upload qua
  `AuthViewModel`. Chạm ID copy clipboard; chạm Email chỉ hiện Toast.
- Dialog Họ và tên bo 16dp, input một dòng và hai nút icon đóng/lưu. Dialog giới
  tính giữ thứ tự Khác, Nữ giới, Nam giới và lựa chọn Bí mật. Dialog đổi mật
  khẩu bo 24dp, ba trường bị che và kiểm tra mật khẩu hiện tại, độ dài 6 ký tự,
  xác nhận trùng khớp.
- Profile được cấp từ listener do `AuthProfileSessionController` quản lý.
  Controller thay listener khi UID đổi, chặn collector trùng và hủy listener,
  profile cache khi logout.

## Ghi chú phạm vi

Checkpoint này chỉ chuyển Profile chính, phần render Edit Profile, presenter và
component dùng chung. Các route con tiếp tục là composable Android và tiếp tục
được mở bằng `ProfileRoute`. Avatar dùng content slot để Android giữ Coil 2 và
photo picker hiện tại; không thêm thư viện ảnh đa nền tảng.

## Hành vi sau migration

- `ProfileUiPresenter` trong `commonMain` nhận `AuthProfileUiState` từ session
  controller và sở hữu loading/error/data, dữ liệu hiển thị, editor tên, dialog
  giới tính, dialog mật khẩu, trạng thái update/upload và event một lần.
- Submit tên/giới tính so sánh với dữ liệu hiện tại trước khi gọi use case. Cờ
  loading được đặt đồng bộ trước khi launch coroutine để chặn lần gọi lặp.
- Avatar chỉ được thay trong state sau khi upload và merge-write đều thành công.
  Hủy picker, lỗi đọc và lỗi upload không sửa URL cũ. URL upload rỗng bị chặn ở
  `UpdateAvatarUseCase` trước khi gọi repository update.
- `ProfileMainContent`, `EditProfileContent`, header, divider, menu row,
  information row và dialog hiện nằm trong `commonMain`. Hai content chỉ nhận
  state, callback Kotlin và avatar content slot, không nhận Android/Firebase.
- `ProfileScreen` Android vẫn sở hữu route, BackHandler, child screen routing,
  share Intent, Toast event và logout callback. `EditProfileView` Android chỉ
  còn photo picker, đọc `Uri` thành `ImageUpload`, clipboard, Toast boundary và
  Coil avatar renderer.
- Một `AuthViewModel` duy nhất được truyền từ navigation qua Home tới Profile;
  presenter chỉ collect StateFlow của session controller và không tạo profile
  repository listener mới. Listener user trực tiếp trước đây trong Home được bỏ;
  streak/date nay lấy từ cùng `User` flow. Logout hủy các operation presenter còn
  chạy, reset editor/UI state; session controller tiếp tục hủy Firestore
  listener/profile.

## Resource và kiểm thử

- Chuỗi dành cho Profile chính/Edit Profile được thêm vào Compose Multiplatform
  Resources. Icon tiếp tục dùng Material icons đã có; không sao chép logo/ảnh và
  không thêm vector hoặc thư viện ảnh mới.
- Common host test bao phủ state mapping, legacy profile, name/gender edit,
  update success/failure, duplicate guard, avatar cancel/read/upload paths,
  event consume, menu event, share event và logout reset. Android unit test bao
  phủ destination mapping, Back chain và trạng thái sub-screen.

Windows không thể chạy iOS simulator/Xcode. Cần macOS để build framework iOS,
kiểm tra resource packaging/font/icon và render/tương tác hai shared content trên
iOS. Checkpoint này không bổ sung Firebase Apple adapter hoặc iOS app wiring.
