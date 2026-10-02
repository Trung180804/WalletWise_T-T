# Android checkpoint: Profile, Home search, financial methods

Branch `fix/android-profile-search-finance`, created from latest `origin/kmp-ios-migration` at `04b3bf37e0523f07207346ed347717be65499595`.
Initial branch `fix/android-issues`, HEAD `edd74a35c55defcaa13e47e3bb257c08f7db021b`.
Initial working tree had only ` M .idea/gradle.xml`; this pre-existing IDE change is preserved.
No staging, commit, push, merge, rebase, cherry-pick, stash, reset, or iOS source changes.

## Profile

Before fix: built current Debug with `-PuseFirebaseEmulator=true`, installed on emulator-5554, prepared a new anonymous Auth Emulator user without a profile, opened Home, inspected the bottom bar, tapped Profile at (975, 2284). Crash occurred at 09:38:19 on 2026-09-16.

Exception: `org.jetbrains.compose.resources.MissingResourceException`, missing `composeResources/com.example.walletwise.shared.resources/values/strings.commonMain.cvr`.
No WalletWise frame/file/line appears in this asynchronous stack: the exception originates inside the resource-loading coroutine. Verified source route: AppNavigation `home` → HomeScreen tab 4 → ProfileScreen MAIN → shared ProfileMainContent/ProfileMainData → `stringResource(Res.string...)` in ProfileContents.kt. AuthViewModel uses ProfileUiPresenter and AuthProfileSessionController/UserRepositoryImpl. Root cause is asset packaging, independent of profile nullability, casts, lifecycle or Firebase callbacks.

APK before fix: **0** `assets/composeResources/` entries. The shared Android KMP target had not opted in to resources. Fix: `androidResources.enable = true` in shared/build.gradle.kts, as required by [official Compose documentation](https://kotlinlang.org/docs/multiplatform/compose-multiplatform-resources-setup.html). Shared UI/resource names and iOS configuration are preserved.

Cold-start login also crashed with the same root cause (`drawable/img.png`); full log: `.artifacts/android-profile-search-finance/before-login.log`.

Session safeguards: cancel old profile collectors/jobs and reset state on UID change/logout/dispose, reject asynchronous results from old generations/canceled coroutines, show safe default/empty profile presentation. Android callbackFlow removes its Firestore listener and rejects late callbacks through the closed channel.

Windows wrapper also failed before Gradle startup because it passed empty `-classpath` before `-jar`; that option was removed. Gradle version stays 9.5.0.

Runtime uses only `demo-walletwise`: Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080, Android host 10.0.2.2. Existing emulator-only rules gained access to the authenticated user's root profile document; never deployed to production. No production test writes/deletes.

Gate A: tái hiện crash trước fix, cùng thao tác Home → Profile sau fix không crash; regression shared và test render/tài nguyên APK PASS. Các state loading/error/empty/content và default profile được kiểm tra riêng. Không dùng try/catch bọc màn hình hoặc nuốt exception. `AuthProfileSessionController` chặn kết quả theo generation/UID; `ProfileUiPresenter` hủy operation và xóa state khi đổi UID/dispose. Listener profile trùng, không được sử dụng trong TransactionViewModel, đã bỏ; listener chính vẫn thuộc AuthViewModel.

Runtime cuối và các tab: xem bảng kết quả bên dưới. Một số lần harness ban đầu thất bại vì tap ngay sau uiautomator dump bị bỏ qua; đã thêm thời gian chờ UI ổn định rồi chạy lại. Đây không phải stack trace crash mới.

## Home search

UI: kính lúp chuyển app bar thành ô tìm kiếm có focus/bàn phím, nút xóa và nút đóng. Search thu gọn thẻ tổng quan/filter/thanh tab, giữ dòng mô tả phạm vi ngày/thanh toán, dùng list dòng ghi chú/danh mục/tiền/ngày và imePadding để bàn phím không che kết quả. Bấm dòng vẫn mở chi tiết và sửa/xóa theo luồng cũ. Query rỗng trả toàn bộ danh sách đang lọc; không khớp hiển thị “Không tìm thấy giao dịch phù hợp”. Back lần một khi bàn phím mở đóng bàn phím; Back tiếp theo đóng search mode, giữ app tại Home. Chuyển tab hoặc UID đóng search; presenter xóa query và dữ liệu phiên cũ khi logout/đổi UID.

Tiền tìm theo cả chữ số không dấu nhóm: `10000000` khớp giao dịch Double `1.0E7`, ngoài query `10.000.000`. Có regression test riêng cho trường hợp này.

Tìm trên transaction StateFlow đang có, sau các filter Home hiện hữu (mặc định ba ngày gần đây); giữ thứ tự mà presenter hiện tại đã sắp xếp. Các trường: ghi chú, danh mục, Thu/Chi, phương thức thanh toán, số tiền thô, tiền định dạng dấu chấm/₫, ngày và giờ hiển thị. Không có Firestore query/write theo ký tự và không mở listener khác.

Chuẩn hóa bằng Kotlin common: trim, lowercase, đổi đầy đủ chữ Việt có dấu sang chữ cơ sở (đ → d), bỏ combining diacritics, gộp whitespace. `an sang`, `AN SANG`, Unicode dấu tách đều khớp `Ăn sáng`. Không import Normalizer/JVM/Android trong commonMain. UI giữ khoảng trắng cuối khi nhập, matcher trim cả hai đầu.

Test PASS: query rỗng/space, hoa thường/bỏ dấu, danh mục, Thu và Chi, thanh toán, tiền thô/định dạng, ngày, no-result, cập nhật live, đổi UID/logout. `TransactionSessionControllerTest.searchPresenterUsesTheExistingTransactionCollector` xác nhận số lần mở collector vẫn là một sau các query. Presenter tài chính cũng chỉ đọc cùng transaction StateFlow của TransactionViewModel.

Bằng chứng runtime ban đầu: `search-accent-match.xml`, `search-empty.xml`, `search-manual-open.xml`, `search-after-back-1.xml`, `search-after-back-2.xml`, `gate-b-manual.log` trong `.artifacts/android-profile-search-finance/`.

## Financial methods

Trước: màn hình Budget ba nhóm, tổng hợp JARS thành 55/10/35 và phân nhóm chi dựa vào tên danh mục. Sau: hai thẻ phương pháp dễ đọc, tên/mô tả/ví dụ/tỷ lệ và nút sử dụng; chọn phương pháp → chọn tháng/thu nhập → xem phân bổ ngay trong dialog rồi lưu. Kế hoạch đã lưu hiển thị tổng thu nhập, tổng chi/tháng, còn lại, 3 hoặc 6 nhóm với phần trăm, tiền và progress; có sửa thu nhập, đổi tháng/phương pháp, đặt lại tỷ lệ và đọc lại kế hoạch tháng. Tỷ lệ cố định để thao tác đơn giản; reset tính lại tỷ lệ mặc định của phương pháp hiện tại. Có lời giải thích đây là gợi ý quản lý tiền, không phải tư vấn đầu tư bắt buộc.

50/30/20: Nhu cầu thiết yếu 50%, Mong muốn 30%, Tiết kiệm/Đầu tư 20%. Thu nhập 10.000.000 ₫ → 5.000.000 / 3.000.000 / 2.000.000 ₫.

6 chiếc lọ theo thứ tự: Nhu cầu thiết yếu 55%, Tự do tài chính 10%, Tiết kiệm dài hạn 10%, Giáo dục 10%, Cho đi 5%, Hưởng thụ 10%. Thu nhập 10.000.000 ₫ → 5.500.000 / 1.000.000 / 1.000.000 / 1.000.000 / 500.000 / 1.000.000 ₫. Cả hai tổng tỷ lệ 100%.

Calculator dùng Long đơn vị đồng: lấy phần nguyên mỗi nhóm, chia lần lượt phần dư một đồng theo thứ tự nhóm đã công bố. Tính quotient/remainder trước phép nhân tránh overflow; tổng luôn bằng thu nhập. Income 0/âm cho zero allocation an toàn ở calculator, không được lưu theo validation. Form chỉ nhận số nguyên dương; quá giới hạn biểu diễn số nguyên chính xác của Double (2^53 − 1) bị từ chối để persistence không mất đồng. Document legacy Double vẫn đọc được, UI trình bày theo đơn vị đồng nguyên.

Shared: FinancialMethod/bucket model, calculator, date adapter interface/calendar, validation/presenter/state/content và unit test. Android chỉ giữ lifecycle/navigation/feedback/Firebase adapter hiện hữu. Không sửa source iOS hoặc thêm Android/Firebase/JVM import vào commonMain.

Persistence: giữ nguyên `users/{uid}/budgets/{MM-yyyy}` và mười field BudgetPlan hiện có; không thêm field/collection, không migration/xóa document. `ruleType` tiếp tục wire `50_30_20`/`JARS`, `totalBudget` và limit dùng Double tương thích Number Long/Double. Sáu nhóm được derive từ rule + total ở shared, ba field limit lưu aggregate tương thích cũ. Save dùng cùng document ID; nhiều lần hoặc đổi phương pháp cùng tháng cập nhật một kế hoạch tháng, không tạo trùng. Kế hoạch các tháng khác giữ nguyên. Giới hạn: schema hiện tại chỉ lưu một phương pháp được chọn mỗi tháng, không lưu đồng thời hai phương pháp trong cùng tháng.

So sánh chỉ hiển thị tổng kế hoạch và tổng chi của tháng đã chọn, không hiển thị nhóm chi theo heuristic. UI ghi rõ chưa có ánh xạ danh mục → nhóm đáng tin cậy. Logic legacy vẫn được giữ để không phá hành vi caller cũ. Không tạo listener transaction mới. Draft đang nhập không bị overwrite khi transaction live cập nhật; đổi tháng giữ draft cùng UID; đổi UID/logout/dispose xóa cả draft, allocation, event và hủy save/collector cũ, chặn callback muộn bằng generation.

Tests: tỷ lệ/10 triệu/0/âm/làm tròn cả hai phương pháp; presenter chọn phương pháp/nhập/reset/validation/loading/error/empty/content/live/đổi tháng/UID/logout/save; session một collector; emulator save/read/upsert/đổi phương pháp/legacy; Compose render số tiền và accessibility tháng/progress. Kết quả thực tế ở dưới.

## Final checks and file inventory

Unit tests chạy mới trên source cuối: `:shared:testAndroidHostTest :app:testDebugUnitTest --rerun-tasks` PASS **272 shared + 23 Android**; failures/errors/skipped đều **0**. Tổng 51 task thực thi, 39s. Log: `final-unit-tests.log` (các log chạy trước cũng được giữ).

`:shared:compileAndroidMain :app:assembleDebug :app:lintDebug --rerun-tasks` PASS, **84 task thực thi**, 2m47s; log `final-build-lint.log`. Debug thông thường không bật Emulator flag được build cuối; APK trên thiết bị là bản source cuối bật Emulator, lưu riêng tại `.artifacts/android-profile-search-finance/emulator-debug.apk`, để runtime không chạm production. Test APK build PASS sau sửa import test Compose không cần thiết (lần đầu fail compile instrumentation; đã sửa và chạy lại).

Instrumentation trên emulator-5554, cửa sổ nhỏ 720×1280, 420 dpi: ProfileRenderRegressionTest 2 + FinancialRenderRegressionTest 1 + HomeSearchRenderRegressionTest 1 → **OK (4 tests)**, 71.761s; log `final-render-tests.log`. Firebase Emulator integration → **OK (1 test)**, 4.596s; log `final-persistence-tests.log`. Firebase demo anonymous session/new profile và seed transaction là test data cô lập, không gọi Firebase production tạo/xóa dữ liệu.

Lint PASS, **0 error, 48 warning** trong báo cáo cuối. Nhóm warning: DefaultLocale 1, InlinedApi 1, OldTargetApi 1, AndroidGradlePluginVersion 2, GradleDependency 7, NewerVersionAvailable 6, ObsoleteSdkInt 1, UnusedResources 9, UseOfNonLambdaOffset 1, IconLocation 1, UseKtx 8, UseTomlInstead 10. Chưa đối chiếu baseline nên không khẳng định tất cả là warning có từ trước. `git diff --check` PASS (exit 0); cảnh báo CRLF của Git không phải lỗi whitespace.

Lần render trên màn hình nhỏ trước đó phát hiện nút Sửa trong dialog chi tiết bị đẩy ra ngoài màn hình. Đã sửa phần nội dung giữa thành vùng cuộn, giữ header và action footer; chạy lại cả bốn test render đều PASS. Form tài chính khóa thao tác sửa thu nhập/phương pháp/tháng khi đang lưu, presenter cũng chặn các event đó; regression PASS. Legacy income 100.9 được làm tròn 101 đồng nhất quán giữa form, tổng hiển thị và phân bổ; regression PASS.

## Runtime cuối và phạm vi kiểm tra

Thiết bị: emulator-5554. APK Debug source cuối bật `useFirebaseEmulator=true`; app ID vẫn `com.example.walletwise`. Log bootstrap xác nhận `demo-walletwise`, Auth `10.0.2.2:9099`, Firestore `10.0.2.2:8080`. Sau kiểm tra màn hình nhỏ/dark, đã đưa thiết bị về 1080×2400, light, portrait. Không xóa dữ liệu app.

| Kiểm tra thực tế | Kết quả và bằng chứng trong `.artifacts/android-profile-search-finance/` |
|---|---|
| Profile user mới không có document | Sau fix hiển thị profile mặc định, không crash. `after-profile-repeat.xml`, `gate-a-runtime.log`. |
| Home và bốn tab khác | XML mới xác nhận Home; lịch tháng 9; AI OCR/form thêm giao dịch; thống kê Tổng quan/Biểu đồ; Profile. `after-home.xml`, `after-history.xml`, `after-add.xml`, `after-statistics.xml`, `after-profile-repeat.xml`. |
| Home search/IME/Back | `an sang` khớp Ăn sáng; no-result đúng text; Back một đóng bàn phím, Back tiếp theo trở lại app bar WalletWise. `search-accent-match.xml`, `search-empty.xml`, `search-after-back-1.xml`, `search-after-back-2.xml`; `search-accent-match.png` kiểm tra cửa sổ nhỏ/dark. |
| 50/30/20 | Nhập 10 triệu, thấy 5/3/2 triệu; đổi tháng 08→09 giữ draft; lưu được. `final-finance-standard-dialog.xml`, `final-finance-previous-month.xml`, `final-finance-standard-saved.xml`. |
| 6 chiếc lọ | Sáu nhóm 5.5/1/1/1/0.5/1 triệu; cuộn tới cuối vẫn có nút lưu. `final-finance-jars-dialog.xml`, `final-finance-jars-bottom.xml`, `final-finance-jars-saved.xml`. |
| Chi thực tế | Với hai transaction test, tổng Chi tháng 9 = 100.000 đ, ngân sách 10 triệu còn 9.900.000 đ; không tự chia theo bucket. `final-finance-standard-saved.xml`, `final-finance-jars-saved.xml`. |
| Xoay màn hình | Home landscape vẫn hiển thị dữ liệu, không crash. `final-rotation-landscape.xml`. |
| Process recreation | Background rồi kill process; PID 21253→22288, mở lại Home/Profile/Budget vẫn đọc kế hoạch JARS 10 triệu. `final-process-recreation.log`, `final-finance-reread-process.xml`, `final-process-runtime.log`. |
| Logout | Xác nhận dialog đăng xuất, app về Đăng Nhập; không fatal. `final-logout-confirmed.xml`, `final-logout-confirmed-runtime.log`. |
| Đổi UID, Profile legacy | Tạo anonymous user B với username/URL ảnh sai, thiếu field profile; Home thu/chi 0 và list rỗng, Profile hiển thị Legacy checkpoint và avatar fallback. `final-legacy-session.log` OK 1 test; `final-user-b-home.xml`, `final-user-b-legacy-profile.xml`, `final-user-b-profile-runtime.log`. |
| Đổi UID, kế hoạch tài chính | User B ở 09-2026 thấy hai lựa chọn phương pháp và không thấy kế hoạch 10 triệu của user A. `final-user-b-finance-empty.xml`, `final-user-b-final-runtime.log`; log cuối không có FATAL EXCEPTION hoặc Caused by. |

Fix gốc ở `shared/build.gradle.kts:17`. Các điểm bảo vệ session: `AuthProfileSessionController.kt:59` đổi generation/UID, `:100` close; `ProfilePresentation.kt:427` close và các operation kiểm tra generation; `TransactionListPresentation.kt:103` xóa query khi UID đổi, `:216` chuẩn hóa tìm kiếm; `BudgetCalculator.kt:72` chia tiền chính xác; `SmartBudgetPresentation.kt:185` bảo vệ save và `:250` dispose. File/dòng đầu tiên thuộc WalletWise **không xuất hiện trong stack bất đồng bộ trước fix**; không có chuỗi `Caused by`, chỉ có `Suppressed` như trace nguyên vẹn bên dưới.

Gate tuần tự: A tái hiện/fix/cùng thao tác Profile và regression PASS rồi mới sửa B; B tests và search thực tế PASS rồi mới sửa C. Việc xác nhận đủ tất cả tab bằng XML mới được hoàn tất ở kiểm tra cuối; không coi các lần tap harness bị bỏ qua lúc đầu là PASS.

Giới hạn kiểm tra: không build iOS trên Windows; không dùng tài khoản/mật khẩu thật hoặc integration production. Runtime logout/login user khác dùng anonymous Auth Emulator. Không chạy riêng stress mất mạng/rapid-tab; crash gốc đã tái hiện nên các kịch bản mở rộng có điều kiện không cần thiết để tìm nguyên nhân. Unit test cuối không có test skipped. Một số lần harness đầu không nhận tap hoặc đọc XML cũ đã được sửa để chỉ chấp nhận dump mới, định vị kính lúp theo accessibility và kiểm tra màn hình bằng text đặc trưng; chỉ báo PASS từ kết quả chạy lại hợp lệ.

## Full Profile stack trace before fix

```text
--------- beginning of main
09-16 09:37:19.146 13575 13575 I FIREBASE_DEBUG_BOOTSTRAP: Default Firebase project=demo-walletwise Auth=10.0.2.2:9099 Firestore=10.0.2.2:8080
--------- beginning of crash
09-16 09:38:19.307 13575 13575 E AndroidRuntime: FATAL EXCEPTION: main
09-16 09:38:19.307 13575 13575 E AndroidRuntime: Process: com.example.walletwise, PID: 13575
09-16 09:38:19.307 13575 13575 E AndroidRuntime: org.jetbrains.compose.resources.MissingResourceException: Missing resource with path: composeResources/com.example.walletwise.shared.resources/values/strings.commonMain.cvr
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.DefaultAndroidResourceReader.throwMissingResourceException(ResourceReader.android.kt:93)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.DefaultAndroidResourceReader.getResourceAsStream(ResourceReader.android.kt:80)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.DefaultAndroidResourceReader.readPart(ResourceReader.android.kt:32)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.StringResourcesUtilsKt$getStringItem$2.invokeSuspend(StringResourcesUtils.kt:26)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.StringResourcesUtilsKt$getStringItem$2.invoke(Unknown Source:8)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.StringResourcesUtilsKt$getStringItem$2.invoke(Unknown Source:2)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at org.jetbrains.compose.resources.AsyncCache$getOrLoad$request$1$1.invokeSuspend(ResourceCaches.kt:28)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlin.coroutines.jvm.internal.BaseContinuationImpl.resumeWith(ContinuationImpl.kt:34)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlinx.coroutines.DispatchedTask.run(DispatchedTask.kt:100)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlinx.coroutines.scheduling.CoroutineScheduler.runSafely(CoroutineScheduler.kt:586)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.executeTask(CoroutineScheduler.kt:829)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.runWorker(CoroutineScheduler.kt:717)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	at kotlinx.coroutines.scheduling.CoroutineScheduler$Worker.run(CoroutineScheduler.kt:704)
09-16 09:38:19.307 13575 13575 E AndroidRuntime: 	Suppressed: kotlinx.coroutines.internal.DiagnosticCoroutineContextException: [androidx.compose.runtime.PausableMonotonicFrameClock@be186da, androidx.compose.ui.platform.MotionDurationScaleImpl@cb9070b, StandaloneCoroutine{Cancelling}@86f89e8, AndroidUiDispatcher@4f29801]
```

## Git cuối và danh sách file chính xác

22 file sửa, 7 file tạo mới, 0 file xóa trong checkpoint. `.idea/gradle.xml` là thay đổi có từ đầu, không thuộc checkpoint; SHA256 vẫn là `9A7B4AD6961AEC3389EAA9F84F1A0136D643B050EC3A1182C2056D25682BE3D1`. Staging rỗng. HEAD và remote base ban đầu giữ nguyên; không commit/push.

| Trạng thái | File | Nhóm commit đề xuất |
|---|---|---|
| Sửa | `app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt` | 2. Home transaction search |
| Sửa | `app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt` | 2. Home transaction search |
| Sửa | `app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt` | 1 + 2 + 3 (tách hunk/method) |
| Sửa | `app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt` | 3. Financial allocation methods |
| Sửa | `firebase/checkpoint-04i/firestore.rules` | 1. Profile crash fix |
| Sửa | `gradlew.bat` | 1. Profile crash fix |
| Sửa | `shared/build.gradle.kts` | 1. Profile crash fix |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt` | 1. Profile crash fix |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt` | 1. Profile crash fix |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt` | 1. Profile crash fix |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt` | 2. Home transaction search |
| Sửa | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt` | 2. Home transaction search |
| Sửa | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt` | 1. Profile crash fix |
| Sửa | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt` | 3. Financial allocation methods |
| Sửa | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt` | 1. Profile crash fix |
| Sửa | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt` | 2. Home transaction search |
| Sửa | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt` | 2. Home transaction search |
| Tạo mới | `app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt` | 3. Financial allocation methods |
| Tạo mới | `app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt` | 2. Home transaction search |
| Tạo mới | `app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt` | 1 + 2 + 3 (tách hunk/method) |
| Tạo mới | `app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt` | 1. Profile crash fix |
| Tạo mới | `docs/checkpoint-android-profile-search-finance.md` | 1 + 2 + 3 (tách hunk/method) |
| Tạo mới | `shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt` | 3. Financial allocation methods |
| Tạo mới | `shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt` | 3. Financial allocation methods |

File dùng chung giữa các nhóm: TransactionViewModel — commit 1 bỏ profile listener trùng, commit 2 query/filter search, commit 3 callback chọn tháng; ProfileCheckpointEmulatorTest — commit 1 prepare missing/legacy profile session, commit 2 seed transaction search, commit 3 persistence budget; docs là báo cáo tổng hợp có thể tách phần cho từng commit. Đây chỉ là đề xuất, chưa stage hoặc tạo commit. `.idea`, APK/build/cache và các harness `.artifacts` không nằm trong đề xuất commit.

`git status --short --untracked-files=all`:

```text
 M .idea/gradle.xml
 M app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
 M app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
 M firebase/checkpoint-04i/firestore.rules
 M gradlew.bat
 M shared/build.gradle.kts
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt
?? docs/checkpoint-android-profile-search-finance.md
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt
```

`git diff --name-status` (Git chưa hiển thị file untracked ở lệnh này; chúng nằm trong status và bảng trên):

```text
M	.idea/gradle.xml
M	app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
M	app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
M	firebase/checkpoint-04i/firestore.rules
M	gradlew.bat
M	shared/build.gradle.kts
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
```

`git diff --cached --name-status`: không có output. `git diff --check`: PASS.
