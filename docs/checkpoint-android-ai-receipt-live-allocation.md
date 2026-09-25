# Checkpoint Android mở rộng: AI, hóa đơn, phân bổ live và nhập tiền

Tiếp tục branch `fix/android-profile-search-finance`, HEAD `04b3bf37e0523f07207346ed347717be65499595`. Giữ Profile/Home Search/Financial Planning trước đó; không rollback/stash/reset/commit/push/merge/rebase. `.idea/gradle.xml` là thay đổi có từ đầu, bảo toàn hash. Báo cáo checkpoint trước: [Profile/Search/Financial methods](checkpoint-android-profile-search-finance.md).

## 1. Root cause AI và phạm vi provider

Trước: Home Compose bottom sheet → TransactionViewModel.processAITransaction → TransactionAIAssistant → Google AI Android SDK `generativeai:0.9.0` → `gemini-2.5-flash`. Credential được hardcode trong source hiện hữu; không dùng BuildConfig để nạp key. Endpoint SDK: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`. Không in/chép credential vào báo cáo; không tạo hoặc rotate key.

Chẩn đoán không inference: GET metadata `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash` với header hiện có (credential đã che) trả **HTTP 401**, error status `UNAUTHENTICATED`, reason `ACCESS_TOKEN_TYPE_UNSUPPORTED`, service `generativelanguage.googleapis.com`, method `ModelService.GetModel`. Provider từ chối authentication của credential hiện có. Không suy diễn đây là DNS/quota/model deprecated; chưa có bằng chứng cho các nguyên nhân đó. HTTP error message đã loại dữ liệu nhạy cảm: “Request had invalid authentication credentials. Expected OAuth 2 access token, login cookie or other valid authentication credential.”

Client cũ catch mọi Exception, log cả message/stack rồi trả null; ViewModel chỉ còn thông báo “Lỗi kết nối AI. Vui lòng thử lại!”. Phân loại quota/503 đặt trong nhánh parse JSON nên không nhận được HTTP failure. JSON optDouble/optString tự default có thể tạo bản nháp thiếu chính xác; timestamp luôn là hiện tại. Code có nút lưu nhưng gọi addTransaction lại tạo ID mới, repository cũng bỏ ID đầu vào và tự tạo document ID, nên retry không idempotent. Reset sheet không hủy analysis và không chặn callback của UID cũ.

Sau: parser deterministic trong commonMain → TransactionDraft có ID/UID/amount integer/type/category/note/time/payment/confidence/missingFields → preview sửa được → Xác nhận lưu → AndroidTransactionWriter → AddTransactionUseCase → TransactionRepositoryImpl → transaction session hiện có. Client cloud hiện hữu trả typed success/failure và rethrow cancellation, không log raw exception. Luồng checkpoint mặc định phân tích cục bộ, không tự gọi cloud vì credential đã bị từ chối và chưa được cấp credential/budget inference mới. **Không tuyên bố cloud generation production PASS.** SDK cũ cũng đã được Google [đánh dấu deprecated](https://github.com/google-gemini/deprecated-generative-ai-android); thông tin auth key hiện hành xem [tài liệu Google](https://ai.google.dev/gemini-api/docs/api-key). Đây là bối cảnh, không thay thế bằng chứng HTTP 401.

Fallback xử lý 50k/50 nghìn/1tr/1 triệu, Thu/Chi rõ ràng, Ăn uống/Lương, hôm qua/hôm nay theo adapter timezone Android. Câu thiếu tiền/mơ hồ/nhiều khoản/âm/overflow cần sửa tay. Thanh toán chưa có bằng chứng để trống cho người dùng chọn. Chỉ dùng category hiện có và đúng type. Không tự ghi transaction trong bước phân tích.

ID draft ổn định xuyên confirm/retry; UI/presenter khóa submit trước khi launch coroutine, success chỉ khi Result chứa true. Repository upsert cùng ID, kiểm tra owner khớp auth UID; legacy/manual transaction ID blank vẫn tự tạo ID. Lỗi/offline giữ draft/form, UID đổi/dispose hủy job và generation chặn callback cũ. Không thêm transaction listener.

Voice dùng external RecognizerIntent, RequestPermission(RECORD_AUDIO), cho phép nhập chữ khi denied/unavailable/cancel và có hướng dẫn mở Settings khi denied. Speech result đi vào ô chat và cùng parser sau khi người dùng bấm tạo draft, không tự lưu.

## 2. Receipt recognition và quyền riêng tư

Android dùng **ML Kit Text Recognition v2 Latin bundled `com.google.mlkit:text-recognition:16.0.1`**, [tài liệu chính thức](https://developers.google.com/ml-kit/vision/text-recognition/v2/android). Camera external TakePicture + gallery GetContent; preview bitmap trong bộ nhớ, nhận diện/hủy/thử lại. Android adapter scale ảnh lớn, đọc EXIF orientation, đóng stream và xóa file camera do app sở hữu sau decode hoặc cancel. Không upload ảnh, không cloud OCR, không persistence raw OCR. Ảnh gallery gốc thuộc người dùng không bị xóa.

Shared ReceiptTextParser ưu tiên dòng tổng rõ ràng, loại subtotal/VAT/khách đưa/tiền thừa; tổng khác nhau cần người dùng chọn, không có tổng thì amount trống. Đọc dd/MM/yyyy, các dạng 50.000/50,000/50 000/50.000đ. Merchant/category theo quy tắc rõ ràng, chỉ đề xuất category expense có sẵn. Payment chỉ từ bằng chứng. Confidence/missingFields nằm trong draft; raw OCR chỉ là biến tạm trong coroutine. Sau nhận diện điền form, người dùng sửa tiền/category/note/date/payment và bấm xác nhận. Save thất bại không xóa form; receipt không đi vào image uploader.

## 3. Live financial allocation

Chưa có plan: hỏi thu nhập dự kiến, không lấy số dư hoặc tự điền 10 triệu. Chọn phương pháp/preview/xác nhận, sửa/reset được; giữ `users/{uid}/budgets/{MM-yyyy}` và wire BudgetPlan. Một UID/tháng một plan active, đổi phương pháp update cùng document.

Calculator Long giữ tổng quỹ bằng đúng thu nhập sau chia phần dư theo thứ tự nhóm. 10.000.000đ, JARS = 5.500.000 / 1.000.000 / 1.000.000 / 1.000.000 / 500.000 / 1.000.000. 50/30/20 = 5.000.000 / 3.000.000 / 2.000.000.

Chi 50.000đ Ăn uống → quỹ thiết yếu JARS: đã chi 50.000, còn **5.450.000**, đã dùng riêng quỹ **0,91%**, còn riêng quỹ **99,09%**, còn so với tổng thu nhập **54,5%**. UI ghi nhãn rõ từng mẫu số. Vượt quỹ giữ tiền/phần trăm còn lại âm, chỉ giới hạn thanh progress để vẽ an toàn.

Nguồn dẫn xuất: plan + transaction session đúng UID/tháng, chỉ Chi; distinct document ID chống snapshot/recurring lặp. Add/edit/delete/snapshot/map change tính lại từ danh sách, không lưu số dư quỹ bằng phép trừ thủ công và không mở listener mới.

Mapping mặc định so khớp category chuẩn chính xác: Ăn uống/Hóa đơn/Nhà cửa/Nhà ở/Y tế/Di chuyển → thiết yếu; Đầu tư → tự do tài chính; Tiết kiệm/Mục tiêu dài hạn → dài hạn; Học tập/Sách/Khóa học → giáo dục; Từ thiện → cho đi; Du lịch/Giải trí → hưởng thụ. Mua sắm/Quà tặng/category mới mặc định chưa phân nhóm để tránh tự quyết định mục đích. 50/30/20 quy về needs/wants/savings; UI xem và chọn lại được từng category.

Persistence mở rộng không phá dữ liệu: optional settings document `users/{uid}/settings/financialCategoryMappings`, key theo ruleType và category name → bucket key, SetOptions.merge giữ phương pháp khác. Không đổi BudgetPlan/transaction schema hoặc document ID. Thiếu document = mapping mặc định an toàn; mapping chưa tải được báo lỗi và chưa trừ vào quỹ; tổng chi chưa phân nhóm hiển thị riêng. Override “unassigned” rõ ràng. Đổi UID/method tải lại mapping và chặn callback muộn. Không sửa category gốc. Dữ liệu hiện hữu không có currency field/conversion: tính theo VND hiện hành, không tạo hoặc giả lập tỷ giá.

## 4. Grouped money input

MoneyInput commonMain giữ raw digits/Long, hiển thị dấu chấm mỗi ba số. `1000000` → `1.000.000`; parse paste dấu chấm/phẩy/space/NBSP. Reject chữ/âm/overflow và số vượt giới hạn integer Double chính xác (2^53−1), giữ wire Double hiện hữu. VisualTransformation có offset mapping; TextFieldValue giữ selection khi paste/chèn/xóa ở giữa. Add/edit/AI draft/receipt amount/plan income dùng cùng formatter, keyboard số, currency suffix nằm ngoài raw input. Không gửi chuỗi có dấu phân cách vào repository.

## Kiểm tra thực tế và Git cuối

### Build, unit và instrumentation

Chạy mới trên source cuối với `--rerun-tasks`, không bật Firebase Emulator trong Android unit tests:

```powershell
.\gradlew.bat :shared:testAndroidHostTest :app:testDebugUnitTest :shared:compileAndroidMain :app:assembleDebug :app:lintDebug --rerun-tasks --console=plain
```

**BUILD SUCCESSFUL trong 2m26s; 92/92 tasks executed.** Shared: **295 tests, 0 failures/errors/skipped**. Android JVM: **25 tests, 0 failures/errors/skipped**. Compile Android shared, assemble Debug và lintDebug PASS. Lint còn **54 warnings**, không có error làm fail task; bảng warning ID phía dưới được lấy từ SARIF. Log: `.artifacts/android-profile-search-finance/extended-final-verified-gradle.log`; XML results ở `shared/build/test-results/testAndroidHostTest` và `app/build/test-results/testDebugUnitTest`.

APK runtime riêng được build với `-PuseFirebaseEmulator=true`, cài cả main/test APK và kiểm tra project thực tế là `demo-walletwise` trước auth/write. Không cài APK normal Debug vào test runtime. Bản sao APK Emulator: `.artifacts/android-profile-search-finance/extended-emulator-debug.apk` và `extended-emulator-test.apk`.

**Instrumentation cuối: OK (7 tests), 42,748s**, log `extended-final-runtime-verified.log`:

| Bộ test | Kết quả / bằng chứng |
|---|---|
| AssistantTransactionEmulatorTest (1) | Parser cục bộ → không có document trước confirm → AddTransactionUseCase/Firebase Emulator ACK → confirm hai lần/retry cùng ID có đúng một document, cùng TransactionSessionController nhận giao dịch; mapping hai phương pháp save/read; clear UID |
| ReceiptOcrRuntimeTest (1) | **ML Kit thật trên Android**, bitmap hóa đơn tổng hợp → tổng 50.000/ngày/category/payment; adapter gallery URI và cleanup; không upload hoặc transaction write |
| LiveAllocationEmulatorTest (1) | Save/read plan 10 triệu, add 50k lặp ID, edit 100k, delete, thêm lại; chi/remaining/phần trăm tính lại từ cùng session, clear UID |
| ProfileRenderRegressionTest (2) | Missing/error/default profile và shared Profile Compose resource không crash |
| HomeSearchRenderRegressionTest (1) | Search shared Compose, query/filter/empty state |
| FinancialRenderRegressionTest (1) | Hai phương pháp, phân bổ và content Compose |

Lượt instrumentation trước chạy đồng thời với Gradle có 6 PASS/1 timeout ở AssistantTransactionEmulatorTest khi chờ trạng thái lưu 20s. Chạy riêng sau build **OK (1 test), 5,432s**; chạy lại cả bộ khi không build song song **7/7 PASS**. Giữ log thất bại `extended-final-runtime-tests.log`, retry `extended-assistant-runtime-retry.log` để không che lịch sử. Không đổi assertion hoặc tăng timeout để làm test qua.

Một lần chuẩn bị ban đầu phát hiện APK đang cài có Firebase production project; guard test dừng **trước** sign-in/write. Sau khi cài đúng APK Emulator thì phát hiện server Auth cũ đã tắt; restart Auth/Firestore Emulator mới, không xóa dữ liệu thật. Các lỗi chuẩn bị này không được tính là production integration PASS.

Shared tests mới gồm parser text/receipt, money typing/delete/paste/overflow/cursor/round-trip, draft confirm hai lần/false ACK/retry ổn định/callback muộn/đổi UID, OCR empty/error/permission/cancel và fake voice gate; tài chính exact ratios/rounding/50k/mapping/unknown/overdraft/add-edit-delete/snapshot/UID/month/Thu/legacy và counting repository xác nhận **observeTransactions chỉ một lần**. Android JVM bổ sung date/time adapter: sửa riêng amount giữ giờ giao dịch, ngày nhận diện hóa đơn không đổi thành hôm nay.

Thời hạn chờ ACK 15s áp dụng AI/manual add/edit, save plan và load/save mapping. Hết thời hạn mở lại submit, giữ form/draft ID và thông báo chưa nhận ACK. Shared test offline sử dụng fake `awaitCancellation()`, virtual timeout rồi retry cùng ID thành công. Firebase SDK có thể vẫn gửi pending write sau khi coroutine ngừng chờ; cùng document ID bảo đảm retry không tạo bản thứ hai. Không tuyên bố đã chạy offline mạng thật chỉ từ test fake này.

### Runtime thủ công và giới hạn

Kế hoạch thật trên Emulator đã được mở ở Profile: JARS 10.000.000đ và giao dịch Ăn uống 50.000đ cho kết quả **5.450.000đ / 0,91% / 99,09% / 54,5%**, nhìn bằng screenshot `extended-final-finance-50000.png`. Xem/sửa mapping, chọn Mua sắm → Hưởng thụ, lưu ACK và mở lại thấy mapping mới (`extended-final-mapping-saved.xml`). Home lúc đó có đúng một synthetic transaction và Profile default Member vẫn mở an toàn.

| Thao tác runtime thật trên emulator-5554 | Kết quả / artifact |
|---|---|
| AI text → preview | Nhập chậm qua bàn phím Android `mua do an 50k tien mat` → Chi/50.000/Ăn uống/Tiền mặt/ngày hiện tại, không có field thiếu; `extended-ai-preview-correct.xml` |
| AI explicit confirm | Hiển thị “Đã lưu giao dịch.” sau ACK; cold restart Home từ 50.000 lên 100.000đ, có một giao dịch mới; `extended-ai-saved.xml`, `extended-home-after-ai.xml` |
| AI incomplete input | Preview yêu cầu bổ sung amount/type/category/payment, không tự lưu; `extended-ai-preview.xml`. ADB nhập một chuỗi dài quá nhanh bị mất ký tự; nhập từng cụm sau khi IME ổn định cho kết quả đúng, không thay source |
| Micro deny | Dialog Android “Don’t allow” → hướng dẫn nhập chữ/mở Cài đặt; `extended-voice-denied.xml` |
| Micro allow/cancel | “While using the app” mở recognizer vi-VN; Google báo **Voice search isn't available**; Back an toàn, không ghi thêm giao dịch; `extended-voice-allowed.xml`. Chưa nhận được transcript runtime |
| Money input | Gõ từng chữ số `1000000` → **1.000.000**, suffix đ; [screenshot](../.artifacts/android-profile-search-finance/extended-money-million.png) |
| Gallery / ảnh không phải hóa đơn | Android Photo Picker mở và preview được ảnh. Một lần chọn ảnh screenshot của chính app → để amount trống, thông báo chưa tìm thấy tổng đáng tin cậy, không tự bịa; `extended-receipt-ocr-complete.png` |
| Gallery / hóa đơn hợp lệ | Chọn đúng fixture QUAN AN TEST → preview → bấm Nhận diện → draft **50.000 / Ăn uống / Tiền mặt / 16/09/2026**, note merchant; [screenshot](../.artifacts/android-profile-search-finance/extended-receipt-valid-complete.png) |
| Camera / cancel / privacy | Camera hệ thống mở với nút Shutter; Back trả lại Add an toàn. `run-as ... ls cache/walletwise_receipt_capture` rỗng sau cancel; không upload. Chưa chụp hóa đơn thật qua camera |
| Receipt rotation / confirm | Landscape rồi portrait giữ amount 50.000 và ảnh preview; form note/date đúng; confirm mới ghi, Home **100.000 → 150.000đ**, thêm đúng một transaction, image URL trống; `extended-receipt-after-rotation.xml`, `extended-receipt-confirm-form.xml`, `extended-home-after-receipt.xml` |
| Logout / UID khác | Logout về Login (`extended-logged-out.xml`). Prepare anonymous session mới trên **demo Emulator** OK (1 test), Home **0đ / Không có giao dịch**, không còn dữ liệu A; `extended-user-b-home.xml` |
| New UID Profile / onboarding | Profile default Member/N không crash; menu **Kế hoạch & Phân bổ** mở tháng 09-2026, hỏi thu nhập với ô **trống**, không có plan của A; `extended-user-b-profile.xml`, `extended-user-b-plan-onboarding.xml` |
| Logcat cuối | `extended-final-runtime.log`: Firebase bootstrap project **demo-walletwise**, Auth/Firestore 10.0.2.2; không có FATAL EXCEPTION, không có credential/email/full UID. Chỉ thu AndroidRuntime:E và bootstrap:I |

Photo Picker đổi từ full-screen sang bottom sheet nên một lệnh tap dùng bounds cũ không chọn được ảnh; harness dừng khi không tìm thấy nút/thumbnail. Chọn lại bằng hierarchy mới và content description của fixture đã thành công. Menu Profile thực tế là “Kế hoạch & Phân bổ”; một probe dùng tên “Quản lý tài chính” cũ bị từ chối, không phải crash app. Các lỗi thao tác harness được giữ trong lịch sử và không tính PASS.

Cleanup: ảnh fixture duy nhất `/sdcard/Pictures/WalletWiseCheckpoint/receipt_synthetic.png` do test tạo đã xóa, giữ ảnh gallery khác và dữ liệu Emulator để review. Windows fixture/screenshot vẫn ở `.artifacts` (ignored). Global animator_duration_scale đã phục hồi trạng thái ban đầu (không có giá trị override); màn hình trở lại portrait. Không xóa transaction/plan thật hoặc tài liệu ngoài checkpoint.

Chưa gọi `generateContent`, chưa kiểm chứng Gemini production hoặc nhận transcript speech thực tế; camera chỉ mở/cancel, chưa chụp hóa đơn thật. HTTP metadata 401 là bằng chứng authentication của **probe metadata**; không có stack trace/logcat generation cũ để khẳng định toàn bộ response của `generateContent`. Không gọi inference có khả năng phát sinh phí hoặc đăng ký credential. AI cloud vẫn là blocker cho đến khi có credential/provider authentication hợp lệ và quyền chi phí; fallback cục bộ hoạt động độc lập.

Ảnh/OCR fixture hoàn toàn tổng hợp, nằm trong `.artifacts` hoặc cache Android và không commit. OCR unit/fake và OCR engine Android thật được phân biệt với thao tác camera/gallery UI. Process recreation của Profile và search/theme/small-screen đã có bằng chứng trong báo cáo checkpoint trước; không suy diễn đó là PASS cho mọi draft đang xử lý ảnh. Draft AI chỉ giữ trong memory; form Add dùng rememberSaveable cho raw money/date/ID/UID, nhận diện đang chạy bị hủy khi session/dispose. Plan/mapping được đọc lại từ repository và chi tiêu được tính lại từ transaction snapshot.

Schema BudgetPlan/Transaction, application ID và source iOS không đổi. Settings mapping là document optional mới, không migration/xóa dữ liệu; nếu production rules hiện hữu chặn settings, UI hiển thị lỗi và giữ chi tiêu chưa phân nhóm, cần triển khai rule riêng có review trước khi dùng production. Chỉ rules Firebase **Emulator** của checkpoint đã sửa; không deploy production. Model transaction hiện hữu không có currency field: hỗ trợ VND hiện tại, chưa hỗ trợ trộn/convert đa tiền tệ. Parser cục bộ xử lý câu rõ ràng và receipt có bằng chứng; câu phức tạp hoặc hóa đơn mờ cần sửa tay. Credential hardcoded hiện hữu được bảo toàn theo giới hạn không sửa credential; không thêm credential mới.

## Danh sách file chính xác và chia commit đề xuất

Snapshot cuối lấy trực tiếp từ Git. M = sửa tracked; ?? = file mới chưa staging. Không có file xóa. `.idea/gradle.xml` đã dirty trước checkpoint, hash SHA256 giữ nguyên `9A7B4AD6961AEC3389EAA9F84F1A0136D643B050EC3A1182C2056D25682BE3D1`, không phải file do checkpoint sửa và không đưa vào commit.

| Git | File | Trạng thái |
|---|---|---|
| M | `.idea/gradle.xml` | IDE có trước, bảo toàn; loại khỏi commit |
| M | `app/build.gradle.kts` | Sửa, chưa staging |
| M | `app/src/main/AndroidManifest.xml` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt` | Sửa, chưa staging |
| M | `app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt` | Sửa, chưa staging |
| M | `firebase/checkpoint-04i/firestore.rules` | Sửa, chưa staging |
| M | `gradlew.bat` | Sửa, chưa staging |
| M | `shared/build.gradle.kts` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt` | Sửa, chưa staging |
| M | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt` | Sửa, chưa staging |
| M | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt` | Sửa, chưa staging |
| M | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt` | Sửa, chưa staging |
| M | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt` | Sửa, chưa staging |
| M | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt` | Sửa, chưa staging |
| M | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt` | Sửa, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/AssistantTransactionEmulatorTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/LiveAllocationEmulatorTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/androidTest/java/com/example/walletwise/reliability/ReceiptOcrRuntimeTest.kt` | Tạo mới, chưa staging |
| ?? | `app/src/main/java/com/example/walletwise/data/draft/AndroidDraftDateTimeProvider.kt` | Tạo mới, chưa staging |
| ?? | `app/src/main/java/com/example/walletwise/data/draft/AndroidReceiptRecognition.kt` | Tạo mới, chưa staging |
| ?? | `app/src/main/java/com/example/walletwise/data/draft/FormTransactionTime.kt` | Tạo mới, chưa staging |
| ?? | `app/src/main/java/com/example/walletwise/data/repository/FinancialMappingRepositoryImpl.kt` | Tạo mới, chưa staging |
| ?? | `app/src/main/java/com/example/walletwise/presentation/home/AITransactionSheet.kt` | Tạo mới, chưa staging |
| ?? | `app/src/test/java/com/example/walletwise/data/draft/FormTransactionTimeTest.kt` | Tạo mới, chưa staging |
| ?? | `docs/checkpoint-android-ai-receipt-live-allocation.md` | Tạo mới, chưa staging |
| ?? | `docs/checkpoint-android-profile-search-finance.md` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/model/TransactionDraft.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/repository/FinancialMappingRepository.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocation.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/MoneyInput.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/ReceiptTextParser.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/domain/service/TransactionDraftParser.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/GroupedMoneyTransformation.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/ReceiptDraftPresenter.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionDraftPresenter.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/VoiceInputGate.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonTest/kotlin/com/example/walletwise/domain/service/DraftAndMoneyTest.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonTest/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocationTest.kt` | Tạo mới, chưa staging |
| ?? | `shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/FinancialMappingPresenterTest.kt` | Tạo mới, chưa staging |

Các file Profile/Home Search và calculator/schema của checkpoint trước được giữ nguyên trong working tree; phân nhóm commit trước đó xem báo cáo `checkpoint-android-profile-search-finance.md`. Bốn commit mở rộng sau đây là đề xuất, **không stage/commit**. File giao thoa cần chia hunk/test theo trách nhiệm khi review, tránh commit toàn bộ file khiến lẫn phần checkpoint trước:

1. **Sửa AI transaction assistant**: `TransactionDraft.kt`, `TransactionDraftParser.kt`, `TransactionDraftPresenter.kt`, `VoiceInputGate.kt`, `AndroidDraftDateTimeProvider.kt`, `AITransactionSheet.kt`, `TransactionAIAssistant.kt`, `AssistantTransactionEmulatorTest.kt`; hunk AI/micro/ACK/idempotency trong `TransactionViewModel.kt`, `TransactionRepositoryImpl.kt`, `HomeScreen.kt`, `AndroidManifest.xml`, test draft/parser trong `DraftAndMoneyTest.kt`.
2. **Thêm receipt recognition**: `AndroidReceiptRecognition.kt`, `ReceiptTextParser.kt`, `ReceiptDraftPresenter.kt`, `ReceiptOcrRuntimeTest.kt`; dependency ML Kit trong `app/build.gradle.kts`, hunk camera/gallery/preview/fill/cancel/no-upload trong `AddTransactionScreen.kt` và `TransactionViewModel.kt`, test receipt/cancel/permission trong `DraftAndMoneyTest.kt`. Dựa trên writer/ID của commit AI.
3. **Hoàn thiện live financial allocation**: `LiveFinancialAllocation.kt`, `FinancialMappingRepository.kt`, `FinancialMappingRepositoryImpl.kt`, `LiveFinancialAllocationTest.kt`, `FinancialMappingPresenterTest.kt`, `LiveAllocationEmulatorTest.kt`; hunk onboarding/live/mapping trong `SmartBudgetPresentation.kt`, `SmartBudgetContent.kt`, `SmartBudgetPlannerView.kt`, test presenter/render/session trong `SmartBudgetPresenterTest.kt`, `FinancialRenderRegressionTest.kt`, `TransactionSessionControllerTest.kt`. Giữ phần FinancialMethod/calculator/schema checkpoint trước làm nền.
4. **Thêm grouped money input**: `MoneyInput.kt`, `GroupedMoneyTransformation.kt`, `FormTransactionTime.kt`, `FormTransactionTimeTest.kt`; hunk raw/selection/formatter/validation/date-preservation trong `AddTransactionScreen.kt`, `AITransactionSheet.kt`, `SmartBudgetContent.kt`, `SmartBudgetPresentation.kt`; test tiền/cursor/overflow trong `DraftAndMoneyTest.kt`. Formatter là dependency presentation của các commit trên; có thể đưa primitives tiền lên trước khi triển khai commit thực tế.

Hai báo cáo docs nằm trong nhóm tài liệu checkpoint. Rules `firebase/checkpoint-04i/firestore.rules`, resource packaging `shared/build.gradle.kts`, `gradlew.bat` và các file Profile/search còn lại thuộc checkpoint trước, không trộn vào sửa AI production configuration. Không đổi app ID, không sửa iOS/plist/local.properties/Firebase production credential.

### Lint warnings thực tế

| Rule ID | Số lượng |
|---|---|
| UseTomlInstead | 11 |
| UnusedResources | 9 |
| UseKtx | 9 |
| GradleDependency | 7 |
| NewerVersionAvailable | 6 |
| ExifInterface | 3 |
| AndroidGradlePluginVersion | 2 |
| UseOfNonLambdaOffsetOverload | 1 |
| IconLocation | 1 |
| DefaultLocale | 1 |
| ObsoleteSdkInt | 1 |
| OldTargetApi | 1 |
| InlinedApi | 1 |
| StaticFieldLeak | 1 |

### git status --short --untracked-files=all

```text
 M .idea/gradle.xml
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
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
?? app/src/androidTest/java/com/example/walletwise/reliability/AssistantTransactionEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/LiveAllocationEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ReceiptOcrRuntimeTest.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidDraftDateTimeProvider.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidReceiptRecognition.kt
?? app/src/main/java/com/example/walletwise/data/draft/FormTransactionTime.kt
?? app/src/main/java/com/example/walletwise/data/repository/FinancialMappingRepositoryImpl.kt
?? app/src/main/java/com/example/walletwise/presentation/home/AITransactionSheet.kt
?? app/src/test/java/com/example/walletwise/data/draft/FormTransactionTimeTest.kt
?? docs/checkpoint-android-ai-receipt-live-allocation.md
?? docs/checkpoint-android-profile-search-finance.md
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/TransactionDraft.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/repository/FinancialMappingRepository.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/MoneyInput.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/ReceiptTextParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/TransactionDraftParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/GroupedMoneyTransformation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/ReceiptDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/VoiceInputGate.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/DraftAndMoneyTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocationTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/FinancialMappingPresenterTest.kt
```

### git diff --name-status

```text
M	.idea/gradle.xml
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
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

Git diff --check exit code: **0**. Index/staging: **0 file**. Không commit/push/merge/rebase/stash/reset/rollback. Git warnings LF→CRLF chỉ là thông báo cấu hình newline; không có whitespace error.
