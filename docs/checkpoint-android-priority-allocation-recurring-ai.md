# Android checkpoint — phân bổ, recurring và AI compact

Ngày kiểm tra: 17/09/2026. Branch `fix/android-profile-search-finance`, HEAD `04b3bf3`.

Tiếp tục working tree có sẵn; giữ các thay đổi Profile, Home Search, OCR, ảnh đính kèm, Money Input và placeholder đã hoàn thành. Không commit, push, reset, rollback, stash, checkout đè, cherry-pick, merge hoặc rebase. Các bằng chứng bên dưới dùng tài khoản/fixture giả trong **Firebase Emulator `demo-walletwise`**, không dùng hóa đơn hay dữ liệu thật.

## 1. Nguyên nhân kế hoạch không giảm

Tái hiện trước sửa bằng dữ liệu cô lập 10.000.000đ, Chi Ăn uống 50.000đ, cùng UID/tháng. Nhánh xử lý lỗi tải mapping trong `SmartBudgetPresentation.kt` ngăn mapping mặc định tham gia calculator: tổng chi có thể thấy 50.000đ nhưng bucket thiết yếu vẫn 0đ; chi tiêu bị chuyển sang nhóm chưa phân nhóm. Test cũ thậm chí kỳ vọng hành vi này. Mapping mặc định cũng chưa được gắn/lưu đầy đủ theo ID danh mục từ category session, nên đổi tên hoặc tải lại không có nguồn mapping ổn định.

Đã bỏ nhánh loại chi tiêu khi mapping lỗi. Lỗi tải vẫn hiển thị và có retry; trong lúc đó calculator dùng mapping mặc định hiệu lực. `AllocationEmulatorUiTest` chủ động gây lỗi tải mapping ở phương pháp 50/30/20, sau đó **repository thật → snapshot → một TransactionSessionController → presenter → Compose** vẫn hiển thị đã chi 50.000đ, còn 4.950.000đ. Đây là bằng chứng dữ liệu đi qua toàn luồng; không chỉ đổi text UI. Không suy diễn rằng đã kiểm tra document production của người dùng.

Log tái hiện trước sửa: [baseline-root-causes.log](../.artifacts/android-priority-revision/baseline-root-causes.log). Trace chạy cuối: [runtime-history.log](../.artifacts/android-priority-revision/runtime-history.log).

## 2. Mọi danh mục có mapping hiệu lực

`FinancialCategoryMapping` ưu tiên key `id:<categoryId>`, sau đó legacy name: trim, lowercase, bỏ dấu tiếng Việt và chuẩn hóa khoảng trắng. Category field legacy chứa ID cũng được resolve qua cùng category source. Alias sinh hoạt, ăn uống, nhà cửa, điện/nước/internet, y tế, di chuyển và giáo dục thiết yếu vào thiết yếu; giải trí, du lịch, mua sắm, làm đẹp, sở thích vào mong muốn/hưởng thụ.

50/30/20 gộp các danh mục tiết kiệm, đầu tư, dự phòng, mục tiêu và trả nợ vào tiết kiệm/đầu tư. Sáu lọ tách đầu tư/tạo tài sản vào tự do tài chính; tiết kiệm/dự phòng/mục tiêu dài hạn vào tiết kiệm dài hạn; học tập/sách/khóa học vào giáo dục; từ thiện/giúp đỡ/quà tặng vào cho đi.

Category tùy chỉnh hoặc override legacy không hợp lệ: **Mong muốn** cho 50/30/20, **Hưởng thụ** cho sáu lọ. Không bỏ giao dịch. Presenter lấy category session hiện hữu, bổ sung và persist ID defaults theo **UID + phương pháp**; người dùng đổi mapping trong cấu hình kế hoạch, toàn tháng tính lại ngay. Đổi tên category giữ mapping ID. Không sửa category gốc của transaction, không thêm category listener.

## 3. Đã bỏ mục chưa phân nhóm

Đã bỏ dữ liệu `unassignedSpent`, nhánh calculator/presenter loại giao dịch và card/option “Chi tiêu chưa phân nhóm”. Mapping picker chỉ có bucket hợp lệ của phương pháp. Test kiểm tra toàn category active đều có bucket; UI test xác nhận node “Chi tiêu chưa phân nhóm” không tồn tại, kể cả khi mapping repository báo lỗi.

## 4. Công thức và bằng chứng add/edit/delete

Nguồn duy nhất: kế hoạch tháng + transaction session đúng UID + mapping hiệu lực. Filter đúng tháng theo date provider/local timezone, loại `Chi` (trim, không phân biệt hoa thường), amount hữu hạn/dương. Dedupe deterministic ID trong snapshot; mọi số dư tính lại từ danh sách hiện tại.

```text
allocated = income × percent
spent = tổng Chi đúng UID/tháng/bucket
remaining = allocated - spent
usedPercent = spent / allocated × 100
remainingPercent = remaining / allocated × 100
remainingIncomePercent = remaining / income × 100
```

| Thao tác thật qua repository | 50/30/20 — thiết yếu | Sáu lọ — thiết yếu |
| --- | ---: | ---: |
| Được cấp | 5.000.000đ | 5.500.000đ |
| Thêm Ăn uống 50.000đ | Chi 50.000đ; còn 4.950.000đ | Chi 50.000đ; còn 5.450.000đ |
| Phần trăm sau thêm | Đã dùng 1%; còn quỹ 99%; còn/thu nhập 49,5% | Đã dùng 0,91%; còn quỹ 99,09%; còn/thu nhập 54,5% |
| Sửa thành 100.000đ | Còn 4.900.000đ | Còn 5.400.000đ |
| Đổi sang Giải trí | Thiết yếu chi 0; Mong muốn chi 100.000đ | Thiết yếu chi 0; Hưởng thụ chi 100.000đ |
| Xóa | Còn 5.000.000đ | Còn 5.500.000đ |
| Snapshot/ghi deterministic phát lại | Không chi lần hai | Không chi lần hai |

Các assert được chạy với Firestore Emulator và Compose thật, không trừ số dư thủ công. Test còn kiểm tra date/type/UID, chuyển tháng, mapping thay đổi, logout và state mapping rỗng sau logout; transaction listener counter bằng **1**.

Ảnh chạy cuối: [50/30/20 thêm](../.artifacts/android-priority-revision/proof/checkpoint-proof/50_30_20-add-50000.png), [sửa](../.artifacts/android-priority-revision/proof/checkpoint-proof/50_30_20-edit-100000.png), [xóa](../.artifacts/android-priority-revision/proof/checkpoint-proof/50_30_20-delete.png); [sáu lọ thêm](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-add-50000.png), [sửa](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-edit-100000.png), [xóa](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-delete.png).

## 5. UI màu trước/sau

Header dùng MaterialTheme primary container tím nhẹ. Mỗi bucket có icon và accent: thiết yếu vàng/cam; mong muốn/hưởng thụ hồng; tiết kiệm dài hạn xanh dương; đầu tư/tự do tài chính xanh lá; giáo dục tím; cho đi cam san hô. Palette đổi độ sáng theo theme/surface để text và tint đọc được ở dark theme.

Capsule cao 16dp, có outline, fill màu bucket thể hiện **đã dùng**, track nhạt thể hiện **còn lại**, legend ghi rõ. Animation 240ms tôn trọng duration scale hệ thống. Dưới 80% bình thường; từ 80% cảnh báo; từ 100% đầy thanh và hiện vượt ngân sách. Chỉ clamp fraction để vẽ; **không clamp số tiền còn lại**.

Ảnh: [trước](../.artifacts/android-ui-allocation-revision/finance-live-walletwise.png), [sau trên ứng dụng](../.artifacts/android-priority-revision/finance-live-walletwise.png), [50%](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-50percent.png), [100%](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-100percent.png), [vượt ngân sách −500.000đ](../.artifacts/android-priority-revision/proof/checkpoint-proof/JARS-over-budget.png). Có ảnh PNG, không quay clip mới.

## 6. Nguyên nhân recurring tự tắt

`RecurringTransaction.timesCount` và new-form default cũ là `1`. Calculator coi lịch hữu hạn theo count. `RecurringTransactionRepositoryImpl.executeIfDue` có hai nhánh tự tắt: nhánh đã xử lý occurrence dùng `copy(isEnabled=false)` khi completed; nhánh tạo occurrence mới dùng `isEnabled=!completed`. Sau lần đầu lịch một lần bị ghi `enabled=false` dù người dùng không tắt.

Đã bỏ cả hai nhánh và mọi limit count khỏi scheduling. New recurring mặc định wire value hiện hữu `Khác` (không giới hạn); UI nói rõ “Không giới hạn — đến khi bạn tắt”. Legacy count `1..7` vẫn đọc được nhưng không tự kết thúc lịch đang bật. Không migration hoặc rewrite hàng loạt document.

## 7. Quyền ghi enabled và race switch

Execution transaction giờ chỉ tạo transaction deterministic và cập nhật **`lastExecutedDate`**; không ghi `enabled`/`isEnabled`. Canonical `enabled` authoritative; `isEnabled` chỉ fallback khi canonical vắng mặt. Toggle người dùng cập nhật canonical và legacy tương thích trong Firestore transaction sau khi re-read document.

Presenter chờ repository confirmation, queue ý định cuối khi bấm nhanh, kiểm tra UID/generation để callback cũ không ghi đè state mới. Reconcile không tin snapshot disabled cũ để cancel nhầm lịch vừa bật; atomic execution re-read quyết định trạng thái. Logout chỉ cancel collector/alarm cục bộ, không tự ghi false vào document.

## 8. Occurrence kế tiếp và idempotency

Daily giữ ngày/giờ anchor, cộng một ngày. Weekly cộng bảy ngày từ anchor; chạy trễ không dịch anchor. Monthly clamp ngày tháng thiếu; yearly giữ tháng/ngày và clamp leap day. Unit test kiểm tra monthly/yearly, leap year và lịch chạy trễ.

Runtime dùng injected clock hợp lệ, không chờ 24 giờ hoặc đổi production cadence. Ngày test 17/09/2026: Daily kế tiếp **18/09/2026**, Weekly kế tiếp **24/09/2026**, cùng giờ anchor. Retry cùng occurrence không tạo thêm transaction, không tắt switch, không mất alarm. Tạo transaction và marker trong một Firestore transaction.

Khi bật lại lịch đã tắt, explicit user-toggle đánh dấu period đã lỡ tới hiện tại là handled, rồi schedule đúng một occurrence tương lai theo anchor; không chạy bù hàng loạt. Việc này là chính sách resume, không phải số dư hoặc count trừ thủ công.

## 9. Toggle off/on runtime và kết nối kế hoạch

`RecurringIndefiniteEmulatorTest` dùng repository Firestore thật, coordinator và AndroidRecurringPlatform thật. Document cố ý có legacy `timesCount=1`, `isEnabled=false`, canonical `enabled=true`. Daily/Weekly chạy occurrence đầu vẫn enabled; retry không thêm transaction. Bấm **Switch Compose thực**: tắt → repository ACK false và PendingIntent bị hủy; bật → ACK true và đúng PendingIntent được tạo lại.

Hai transaction Daily/Weekly đi vào một TransactionSessionController thật: **source=2, listeners=1, thiết yếu đã chi 100.000đ, còn 4.900.000đ** với kế hoạch 50/30/20. Fresh coordinator reconcile không nhân đôi scheduling; clearUser không ghi false. Đã chạy thêm prepare → **ADB reboot thật** → đợi `sys.boot_completed=1` → verify phục hồi auth/marker/alarm, không tạo transaction ngoài ý muốn.

Ảnh mới: [sau occurrence đầu](../.artifacts/android-priority-revision/recurring-proof/priority-proof/recurring-first-enabled.png), [tắt](../.artifacts/android-priority-revision/recurring-proof/priority-proof/recurring-toggle-off.png), [bật lại](../.artifacts/android-priority-revision/recurring-proof/priority-proof/recurring-toggle-on.png).

## 10. AI compact và bàn phím

Sheet wrap content, không weight ép hội thoại hoặc Spacer lớn, max 60% màn hình. Header/composer đo chiều cao thực; vùng hội thoại dùng phần còn lại và cuộn khi nhiều nội dung/thumbnail. Trên emulator 1080×2400, sheet rỗng bắt đầu y=1633, khoảng **32% màn hình**. Khi mở keyboard, camera/thư viện/mic và input nằm y=1344..1470 phía trên keyboard. Runtime test còn kiểm tra vùng host **320×260dp** sau OCR: cả ba nút, input và send vẫn hiển thị.

Trắng/hồng, góc trên bo tròn, drag handle, thứ tự camera → thư viện → mic, touch target 48dp và content description được giữ. Mô tả nói rõ tự ghi khi đủ dữ liệu, hỏi thêm khi thiếu; không còn mô tả draft/xác nhận chung.

Ảnh [trước](../.artifacts/android-ui-allocation-revision/ai-after.png), [sau](../.artifacts/android-priority-revision/ai-after.png), [keyboard](../.artifacts/android-priority-revision/ai-keyboard.png).

## 11. Tham khảo commit ed833e

Đã kiểm tra object là commit, xem stat, và dùng `git show ed833e02e32f709b90ef5c9db541d8a9a3bb8f39 -- app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt` để nghiên cứu phần AI liên quan. Bố cục cũ là wrap-content Column, status/input gần nhau và imePadding. **Commit đó vẫn có “Xác nhận & Lưu”**, nên không tuyên bố auto-save đã tồn tại ở commit tham chiếu. Khôi phục bố cục gọn theo tham chiếu; auto-save là yêu cầu nghiệp vụ mới. Không checkout/revert/cherry-pick commit hoặc sửa .idea của commit đó.

## 12. AI tự ghi không dùng bước draft xác nhận

Android route gọi `submitAutomatically` cho text/voice và `acceptReceiptAutomatically` cho OCR. Presenter vẫn dùng model draft nội bộ để giữ dữ liệu thiếu, idempotent ID và hỗ trợ API cũ; **không có bước preview draft → confirm bắt buộc trong UI text/voice**.

Câu “Hôm nay mua đồ ăn 50 nghìn” được parse Chi/50.000đ/Ăn uống/hôm nay, chọn category ID từ nguồn hiện hữu và dùng payment mặc định Tiền mặt như form hiện tại. Gọi transaction write use case cũ; success chỉ sau repository ACK. Câu thiếu amount hỏi amount; category mơ hồ không chọn bừa. Bổ sung trường thiếu giữ entry ID, không bịa ngày OCR. Đã bổ sung test hai entry khác nhau có cùng câu trả lời “50 nghìn” vẫn được ghi, trong khi submit/callback lặp của cùng entry không ghi lần hai.

OCR confidence cao (ngưỡng 0,8), có total duy nhất/ngày/category rõ: tự ghi một lần, hiển thị thumbnail và tóm tắt. Nhiều total/confidence thấp: yêu cầu chọn/bổ sung trước khi tự ghi. Không upload receipt. Retry lỗi ghi dùng cùng ID; analysis/write jobs riêng tránh race với Main.immediate và cancel đúng khi logout.

Bằng chứng: `ReceiptAttachmentFlowRenderTest` chạy ML Kit thật với fixture giả, ACK/session thật cho camera/picker/text/voice; `AssistantTransactionEmulatorTest` kiểm tra deterministic document; `AutomaticTransactionEntryTest` kiểm tra thiếu/mơ hồ/retry/idempotency/logout. Lần gửi trực tiếp câu không dấu tương đương trên ứng dụng tạo thêm đúng một giao dịch 50.000đ: Home từ một giao dịch/tổng chi 50.000đ thành hai giao dịch/tổng chi 100.000đ. Sheet hiển thị **“Đã ghi Chi 50.000 đ • Ăn uống”**, không có confirm. Ảnh: [ai-auto-saved.png](../.artifacts/android-priority-revision/ai-auto-saved.png).

## 13. Giữ OCR/attachment/category/No image đã PASS

AI camera và picker đi vào OCR; Add Transaction camera/picker chỉ cập nhật attachment, không gọi OCR. Test upload ảnh thất bại không báo lưu thành công giả; không ảnh vẫn ghi được. Permission denied/cancel không crash, temp receipt lifecycle được giữ.

Category Add Transaction lấy category repository/session hiện hữu, theo thứ tự nguồn rồi `take(8)`, không hard-code hoặc listener mới. Edit category ngoài top tám giữ selection an toàn khi save. Các test này được chạy lại và PASS trong bộ 33.

No image vẫn dùng component Compose chung, null/blank/whitespace không gọi loader; URL lỗi fallback; khung cố định và accessibility. Không ghi/upload placeholder hoặc đổi URL database. Hai render tests và ảnh [trang lịch sử](../.artifacts/android-priority-revision/history-no-image.png) xác nhận regression. Chọn ngày 17 giữ hành vi hiện hữu chuyển sang danh sách giao dịch lọc ngày: [hai card không ảnh](../.artifacts/android-priority-revision/history-day-no-image.png).

## 14. File tạo/sửa/xóa trong lượt ưu tiên này

File mới của lượt ưu tiên: `FinancialBucketPalette.kt`, `AutomaticTransactionEntryTest.kt`, `RecurringIndefiniteEmulatorTest.kt` và báo cáo này. Không xóa file source.

Các file source/test được cập nhật, gồm file đã tồn tại dưới dạng untracked ở checkpoint trước:

- Phân bổ: `LiveFinancialAllocation.kt`, `SmartBudgetPresentation.kt`, `SmartBudgetContent.kt`, `BudgetUsageTube.kt`, `TransactionViewModel.kt`, `LiveFinancialAllocationTest.kt`, `AllocationCheckpointRegressionTest.kt`, `FinancialMappingPresenterTest.kt`, `AllocationEmulatorUiTest.kt`, `FinancialRenderRegressionTest.kt`.
- Recurring: `RecurringTransaction.kt`, `FirestoreWireMapper.kt`, `RecurringScheduleCalculator.kt`, `RecurringScheduling.kt`, `RecurringPresentation.kt`, `RecurringContent.kt`, `RecurringTransactionRepositoryImpl.kt`, `RecurringScheduleCalculatorTest.kt`, `RecurringAutomationCoordinatorTest.kt`, `RecurringTestFakes.kt`, `RecurringUiPresenterTest.kt`, `FirebaseReminderRecurringEmulatorTest.kt`.
- AI: `AITransactionSheet.kt`, `TransactionDraftPresenter.kt`, `TransactionDraftParser.kt`, `TransactionViewModel.kt`, `AssistantActionsRenderTest.kt`, `AssistantTransactionEmulatorTest.kt`, `ReceiptAttachmentFlowRenderTest.kt`.

`TransactionViewModel.kt` có cả wiring AI và category session cho budget; khi tách commit cần chia hunk. Các script, APK, screenshot, fixture và log trong `.artifacts/android-priority-revision` được Git ignore. Full status/diff cuối bao gồm mọi thay đổi có sẵn, không đồng nghĩa tất cả được tạo trong lượt này.

## 15. Build/test mới cuối cùng

| Lệnh hoặc bộ kiểm tra | Kết quả thực tế |
| --- | --- |
| `.\gradlew.bat :shared:testAndroidHostTest --rerun-tasks` | PASS — **316 tests**, 0 failures/errors/skipped |
| `.\gradlew.bat :app:testDebugUnitTest --rerun-tasks` | PASS — **25 tests**, 0 failures/errors/skipped |
| `.\gradlew.bat :shared:compileAndroidMain` | PASS |
| `.\gradlew.bat :app:assembleDebug` | PASS |
| `.\gradlew.bat :app:lintDebug` | PASS — 0 errors, **55 warnings** |
| `git diff --check` | PASS |
| APK Firebase Emulator + Android test APK | Build PASS; guard `USE_FIREBASE_EMULATOR=true`; install PASS |
| Regression Android trên source cuối | PASS — **33 tests**, 0 failed/ignored, 211,215s |
| Prepare real reboot + verify sau boot ready | PASS — từng test prepare/verify; verify 0,722s |

Log cuối: [shared-tests](../.artifacts/android-priority-revision/shared-tests.log), [app-tests](../.artifacts/android-priority-revision/app-tests.log), [compile](../.artifacts/android-priority-revision/shared-compile.log), [assemble](../.artifacts/android-priority-revision/app-assemble.log), [lint](../.artifacts/android-priority-revision/app-lint.log), [33 runtime tests](../.artifacts/android-priority-revision/final-checkpoint-regression-tests.log), [reboot prepare](../.artifacts/android-priority-revision/final-source-and-boot-prepare-tests.log), [reboot verify](../.artifacts/android-priority-revision/real-reboot-verify-tests.log). XML test results và lint HTML/XML nằm trong build reports.

## 16. Lịch sử fail và giới hạn runtime

Không che các lần thất bại trước kết quả cuối:

- Unit test cũ kỳ vọng fallback index và fixture rapid-toggle không phát flow: điều chỉnh theo nghiệp vụ/fix fixture, sau đó bộ đầy đủ PASS.
- Lần gọi quá rộng 44 runtime test có 7 fail ở `RuntimeRecoveryEmulatorTest` vì thiếu tham số và bước OS orchestration cho timezone/Doze/boot/permission; các nhóm khác PASS. Không tính lần đó là regression PASS. Sau đó chạy scope đúng và real reboot đúng harness PASS.
- Nhóm 9 test từng có 1 fail do PixelCopy timeout khi lấy ảnh; business assert đã qua. Đổi screenshot helper sang UiAutomation capture, giữ nguyên assert; rerun test và bộ 33 cuối PASS.
- Verify gọi quá sớm sau reboot chưa bắt đầu được instrumentation. Log có `locales is empty` ở `ActivityThread.handleBindApplication` và lỗi dịch vụ Android chưa sẵn sàng; có lần System UI ANR trên emulator. Chờ boot-ready, đóng dialog hệ thống rồi rerun verify PASS. Không khẳng định toàn bộ lịch sử log không crash.
- Lần script manual Finance đầu chạm tab trước khi Home ổn định nên không tìm thấy action; lấy UI root mới và thao tác trên Home đã tải xong thành công. Không thay business test để bỏ qua lỗi.
- Script chụp sau gửi AI từng bấm Back khi keyboard đã tự đóng, nên đóng sheet và check text ACK không thấy. Home đã cập nhật đúng hai giao dịch/tổng chi 100.000đ; mở lại sheet lấy được ACK “Đã ghi Chi 50.000 đ • Ăn uống”. Không gửi lại hoặc tạo thêm giao dịch để sửa bằng chứng.

Đã chạy thực tế: OCR ML Kit synthetic; AI text tự ghi và hỏi thiếu; voice callback qua cùng write flow; real Firestore transaction/idempotency; hai phương pháp add/edit/category/delete; progress 0/50/100/>100; logout; Android PendingIntent; recurring off/on; fresh coordinator và reboot thật; Profile/Search/No image/attachment regression.

Chưa chạy lại toàn bộ **OS orchestration Doze/timezone/time-change/notification-revocation/commit-after-failure** trong lượt ưu tiên này; các phần đã PASS trước được giữ, unit/Android reliability và atomic failure tests hiện tại đã chạy. Chưa kiểm tra microphone ASR bằng giọng nói thật, thiết bị vật lý, real provider/API/upload production hoặc iOS native tests. Launcher callback OCR/voice dùng harness; không gọi API tính phí. Camera/picker hệ thống đã có bằng chứng checkpoint trước, không làm lại mọi luồng đã PASS.

## 17. Warning/rủi ro còn lại

Lint hiện có 55 warnings (0 errors), gồm cảnh báo dự án/checkpoint hiện hữu; log Gradle còn deprecation Compose icon và Native iOS task disabled vì host Windows. Đây không phải Android test skip.

Khoảng log manual cuối được tách sau khi lưu toàn bộ lịch sử: [runtime-final-manual.log](../.artifacts/android-priority-revision/runtime-final-manual.log), không có FATAL EXCEPTION hoặc mẫu credential/token được kiểm tra. Hai thư mục cache ảnh receipt/attachment thuộc app đều rỗng sau lifecycle; không xóa dữ liệu thật hoặc ảnh gallery. Không suy rộng kiểm tra mẫu token này thành audit toàn bộ bảo mật.

Unknown category tự fallback mong muốn/hưởng thụ theo yêu cầu; người dùng có thể cần chỉnh mapping hợp lý cho danh mục tùy chỉnh. Lịch legacy count hữu hạn đang bật nay hoạt động không giới hạn theo nghiệp vụ mới, không còn ngừng theo count. Bật lại bỏ occurrence quá hạn có chủ đích để tránh backfill.

APK cài trên emulator là bản demo Firebase Emulator. APK assemble thông thường chạy cuối không được cài đè lên emulator; bản demo và test APK được lưu riêng trong `.artifacts`. Kết quả emulator không chứng minh hành vi mọi thiết bị/production.

## 18. Bảo vệ working tree và cấu hình

Kiểm tra trước sửa: branch, full status, diff name-status, log 10 commit và `git cat-file -t` commit tham chiếu. Chụp hash `.idea/gradle.xml`, `local.properties`, `app/google-services.json` trước khi làm; kiểm tra cuối phải **3/3 unchanged**. `.idea/gradle.xml` đã dirty từ đầu, được giữ nguyên. Không sửa credential/Firebase production config; không sửa trực tiếp `kmp-ios-migration`.

Git output đầy đủ và kiểm tra staging được đính kèm ở cuối báo cáo. File untracked có trong `git status`; `git diff --name-status` chỉ liệt kê file tracked đã sửa.

## 19. Đề xuất ba commit độc lập — chưa tạo

1. **Fix category-based financial allocation** — calculator/default ID mapping/persistence/theme UI và test phân bổ; lấy hunk category-session wiring của ViewModel.
2. **Keep recurring transactions active until user disables them** — unlimited default, atomic marker-only execution, toggle/reconcile/anchor và unit/emulator tests; giữ các hunk mapper có sẵn không liên quan.
3. **Restore compact automatic AI transaction entry** — compact sheet, automatic text/voice/OCR, missing-field questions/idempotency và AI tests; lấy hunk AI ViewModel. Các phần Profile/Search/attachment/No image từ checkpoint trước có thể tách riêng khi người dùng yêu cầu commit.

## 20. Staging và hoàn tất

Không stage, commit hoặc push. Báo cáo cùng source/test để người dùng review. Ảnh và log runtime được lưu cục bộ, fixture không có thông tin cá nhân. Git verification cuối kiểm tra branch/HEAD, staging sạch, diff-check và hash cấu hình; output dưới đây là trạng thái toàn working tree sau khi hoàn tất.

## Git output cuoi

Branch: `fix/android-profile-search-finance`. HEAD: `04b3bf3`. Staging clean: **True**. `git diff --check`: **PASS**. Protected hashes: **3/3 unchanged**.

### git status --short --untracked-files=all

```text
 M .idea/gradle.xml
 M app/build.gradle.kts
 M app/src/androidTest/java/com/example/walletwise/reliability/FirebaseReminderRecurringEmulatorTest.kt
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/example/walletwise/data/repository/RecurringTransactionRepositoryImpl.kt
 M app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HistoryScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
 M app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
 M firebase/checkpoint-04i/firestore.rules
 M gradlew.bat
 M shared/build.gradle.kts
 M shared/src/commonMain/kotlin/com/example/walletwise/data/mapper/FirestoreWireMapper.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/model/RecurringTransaction.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/model/Transaction.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/RecurringScheduleCalculator.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/RecurringScheduling.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/recurring/RecurringContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/recurring/RecurringPresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/domain/service/RecurringAutomationCoordinatorTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/domain/service/RecurringScheduleCalculatorTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/recurring/RecurringTestFakes.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/recurring/RecurringUiPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AllocationEmulatorUiTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AssistantActionsRenderTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AssistantTransactionEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/LegacyFallbackSnapshotEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/LiveAllocationEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ReceiptAttachmentFlowRenderTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ReceiptOcrRuntimeTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/RecurringIndefiniteEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/TransactionPhotoRenderTest.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidDraftDateTimeProvider.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidReceiptRecognition.kt
?? app/src/main/java/com/example/walletwise/data/draft/FormTransactionTime.kt
?? app/src/main/java/com/example/walletwise/data/image/TransactionAttachmentState.kt
?? app/src/main/java/com/example/walletwise/data/repository/FinancialMappingRepositoryImpl.kt
?? app/src/main/java/com/example/walletwise/presentation/home/AITransactionSheet.kt
?? app/src/main/java/com/example/walletwise/presentation/home/TransactionPhoto.kt
?? app/src/test/java/com/example/walletwise/data/draft/FormTransactionTimeTest.kt
?? docs/checkpoint-android-ai-receipt-live-allocation.md
?? docs/checkpoint-android-priority-allocation-recurring-ai.md
?? docs/checkpoint-android-profile-search-finance.md
?? docs/checkpoint-android-ui-ocr-allocation-placeholder.md
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/TransactionDraft.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/repository/FinancialMappingRepository.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/MoneyInput.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/ReceiptTextParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/TransactionDraftParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetUsageTube.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/FinancialBucketPalette.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/GroupedMoneyTransformation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/NoTransactionImage.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/ReceiptDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionCategoryChoices.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/VoiceInputGate.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/AllocationCheckpointRegressionTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/AutomaticTransactionEntryTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/DraftAndMoneyTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocationTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/FinancialMappingPresenterTest.kt
```

### git diff --name-status

```text
M	.idea/gradle.xml
M	app/build.gradle.kts
M	app/src/androidTest/java/com/example/walletwise/reliability/FirebaseReminderRecurringEmulatorTest.kt
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/com/example/walletwise/data/repository/RecurringTransactionRepositoryImpl.kt
M	app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HistoryScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
M	app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
M	firebase/checkpoint-04i/firestore.rules
M	gradlew.bat
M	shared/build.gradle.kts
M	shared/src/commonMain/kotlin/com/example/walletwise/data/mapper/FirestoreWireMapper.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/model/RecurringTransaction.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/model/Transaction.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/RecurringScheduleCalculator.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/RecurringScheduling.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/recurring/RecurringContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/recurring/RecurringPresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/domain/service/RecurringAutomationCoordinatorTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/domain/service/RecurringScheduleCalculatorTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/recurring/RecurringTestFakes.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/recurring/RecurringUiPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
```

Output includes all pre-existing changes. No files have been staged, committed or pushed.
