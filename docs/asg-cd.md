# CD-ASG-01 — Backend CD qua CloudFormation và Instance Refresh

## Hợp đồng tám phần

1. **Mục tiêu/actor/phạm vi:** chủ repo phát hành backend đã qua CI trên main
   tới cả ASG, kể cả instance được tạo sau release. Thay luồng một EC2 trong
   workflow CD; UI/S3 pipeline, tính năng Payment và schema ngoài phạm vi.
2. **Sở hữu/phụ thuộc:** CloudFormation tiếp tục sở hữu Launch Template/ASG;
   container app dùng RDS/S3 và Secrets Manager hiện hành. Base source đã hợp
   nhất từ PR47; không lấy chuỗi migration V25/V26 cũ vào release này.
3. **Bất biến:** chỉ đổi ApplicationImage của template Original đã triển khai;
   mọi parameter khác dùng giá trị trước. Không gọi deploy.sh/Compose lên ASG,
   không chạy MySQL/MinIO trên app host, không ép refresh/terminate thủ công.
4. **An toàn/vận hành:** OIDC chỉ repo backend/main/environment production,
   quyền giới hạn stack và fleet. Từ chối stack/fleet bận, thiếu target, lab
   đang tắt, template lệch hoặc plan đụng tài nguyên ngoài app. Không đọc env,
   secret/token hoặc xuất log thô. Mọi rollout có timeout và hậu kiểm cả fleet.
5. **Đầu ra:** controller CLI có plan/apply/verify, workflow CD mới, tests mô
   phỏng AWS, CI quality gate và hướng dẫn IAM/GitHub/manual trial/rollback.
   Giữ các cờ tự động tắt tới khi có bằng chứng đợt chạy thử được duyệt.
6. **Ca kiểm tra:** fleet2/4, bootstrap scale-out dùng LT mới, zero/offline/
   unhealthy target, sai account/repo/digest, drift/stale plan, thay DB/IAM,
   update rollback/fail/timeout, probe thiếu instance và invocation giả success.
7. **Gate:** Node unit/scenario tests, actionlint, shell/Compose compatibility,
   source secret review, required verify/dependency-review, CI main; AWS plan
   validation và trial có kiểm soát trước khi bật tự động. Không gọi test mock
   là trial AWS. Branch coverage backend >80% vẫn là gate còn lại của dự án.
8. **Rollout/rollback:** build/scan/push image trước, change set image-only sau;
   native CloudFormation Instance Refresh giữ healthy100%, max150%, không
   restart đồng loạt. Ghi previous image; rollback image qua change set tương
   tự, giữ RDS/schema/Flyway. Khi rollback thất bại hoặc trạng thái không chắc,
   dừng và cần xử lý vận hành, không tự ép terminate hay sửa dữ liệu.

## Trạng thái

Đang nghiệm thu CD-ASG-01 ngày26/10/2026. PR47 đã merge tại
`132d4a9b87aae65fa5ce88450de075962111b558`; verify của merge commit xanh,
dependency-review skipped đúng điều kiện push. Docker Hub token đã được chủ
repo Store; chỉ kiểm tên `DOCKERHUB_TOKEN`, không đọc token. CD source qua
[PR48](https://github.com/triramisu/shop/pull/48), merge `1473871`; required
checks và CI merge commit xanh. Role riêng đã được duyệt/tạo. Manual gate
ASG_CD_ENABLED=true; ASG_AUTO_DEPLOY_ENABLED=false. Chưa rollout image mới.

161 test Node đạt, gồm 67 ca CD/source/CLI contract và 94 ca preflight/template/proxy;
actionlint đạt. Probe thật ở Singapore xác nhận **2/2 container Healthy,
đúng digest, restart0, không OOM, LT8**. Chỉ đọc Docker metadata bằng SSM;
đây là kiểm tra image hiện hành, chưa phải nghiệm thu rollout image mới.

[Verify trên runner GitHub](https://github.com/triramisu/shop/actions/runs/40288468566)
đã đạt qua OIDC thật: ALREADY_DEPLOYED, verifiedInstances2, LT8, source1473871.
Không tạo change set hoặc refresh. Lần build/plan
[40288612662](https://github.com/triramisu/shop/actions/runs/40288612662) đã bị
Trivy chặn trước push/deploy: brotli-libs1.2.0-r1, HIGH CVE-2026-111497,
bản vá1.2.1-r0. Dockerfile bổ sung upgrade riêng package từ repository Alpine
hiện hành; không đổi distro/Java/user, không hạ scan gate hoặc ignore CVE.
Build local đạt, apk xác nhận1.2.1-r0, Temurin21.0.12.1 và UID10001; Trivy
0.75.0 cùng flags CD trả exit0, không có HIGH/CRITICAL có bản vá trong OS
Alpine3.24.3. Chưa thay thế kết quả scan/push trên runner hoặc trial rollout.
[Alpine xác nhận bản vá](https://security.alpinelinux.org/vuln/CVE-2026-111497),
[upstream release](https://github.com/google/brotli/releases/tag/v1.2.1).

[PR49](https://github.com/triramisu/shop/pull/49) đã qua required checks,
merge `56d76a7`; CI merge commit xanh. Build/preview mới đang chạy tại
[40291506938](https://github.com/triramisu/shop/actions/runs/40291506938),
chưa Execute hoặc thay EC2. Không đổi Java/schema trong bản vá.

Run40291506938 đã vượt scan và push image thành công; dừng ở DescribeEvents
vì script truyền filter EventType không thuộc model AWS CLI. Không Execute;
plan vẫn CREATE_COMPLETE/AVAILABLE với đúng3 Modify, không Add/Remove.
Đã sửa lấy đầy đủ event theo change-set ARN và phân loại ở client, giữ chặn
validation/provisioning/hook error kể cả WARN. Không dùng FailedEvents=true
để lọc mất warning. Chờ stack-event SUCCEEDED, không chấp nhận response rỗng
hoặc thiếu trang; lưu ARN/image vào artifact ngay cả khi validation thất bại.

Test mới dùng `--generate-cli-skeleton output` với credential giả và tắt IMDS
để AWS CLI tự kiểm đúng arguments, không gọi AWS thật; thêm ca WARN, unknown
event, pending/incomplete và CRLF Windows/LF Linux. Fingerprint chỉ chuẩn hóa
CRLF; đổi nội dung/space/comment vẫn là drift. Xem
[DescribeEvents](https://docs.aws.amazon.com/cli/latest/reference/cloudformation/describe-events.html).

PR50 đã qua required checks và merge `b707a78`, CI main xanh. Preview runner
40295660959 tạo đúng3 Modify nhưng DescribeEvents bị AccessDenied khi request
chỉ có change-set ARN. Controller đang bổ sung cả stack ARN trong request và
kiểm StackId trên mọi event; giữ nguyên IAM ARN scope, chưa cấp DescribeEvents
Resource=*. Đã đối chiếu bằng API read-only: selector stack+ARN vẫn trả đúng2
event cùng operation/change set, không đọc nhầm toàn bộ lịch sử stack.
Chưa xác nhận selector mới qua OIDC runner hoặc Execute; website vẫn RC2/LT8.

Lệnh cũ trong docs/runbook/ci-cd.md là luồng single-host lịch sử; không dùng
deploy/deploy.sh cho ASG dùng RDS/S3.

## Luồng vận hành

```text
PR -> verify + dependency-review -> merge main -> CI main xanh
   -> kiểm đúng SHA main hiện tại -> build + scan OS -> push Docker Hub
   -> digest -> change set ApplicationImage -> kiểm plan/validation
   -> CloudFormation cập nhật LT -> native ASG Instance Refresh
   -> target Healthy -> SSM kiểm toàn bộ instance hiện tại -> lưu report
```

Đổi `--instance-ids` thành tag khi chạy deploy.sh không giải quyết máy được
tạo sau này. Ở đây image nằm trong UserData của Launch Template do
CloudFormation quản lý; EC2 được tạo khi scale-out cũng nhận image mới.
SSM chỉ hậu kiểm: script đọc danh sách hiện tại từ ASG mỗi lần, rồi gửi tới
đúng tập instance đó. Không lưu `EC2_INSTANCE_ID` trong GitHub Variables.

Controller từ chối template Original khác source, stack đang update, refresh
đang chạy, lab Desired=0, target thiếu/unhealthy, LT/AMI lẫn phiên bản, thiếu
hai AZ hoặc plan đụng tài nguyên ngoài LT/ASG/scaling policy. Mọi parameter
ngoài ApplicationImage phải giữ nguyên, kể cả AMI, payment và secret ARN.
Không chạy đồng thời rollout với load test hoặc thao tác console ASG.

## IAM và OIDC đã chuẩn bị để review

Hai file không chứa credential:

- [Trust policy](../deploy/aws/iam/backend-github-trust.json).
- [Một permissions policy duy nhất](../deploy/aws/iam/backend-github-permissions.json).

Role mới `shop-backend-github-deploy`, MaxSessionDuration=5400 giây, dùng
OIDC provider GitHub đã tồn tại. Không sửa role `shop-ui-github-deploy`,
không đặt service role mới lên toàn bộ production stack.

Chủ repo duyệt role/GitHub trong phiên ngày26/10. Đã tạo role và attach đúng
inline policy `BackendAsgImageRelease`, đọc lại duration5400 và policy name;
GitHub environment chỉ có branch main. Mô phỏng IAM đạt13 ca allow/deny trước
attach; assume OIDC và probe thật đã đạt ở run liên kết phía trên. Chỉ cờ
manual được bật để nghiệm thu; auto vẫn false, chưa rollout image mới.

Đối chiếu CloudTrail Event History của rollout kho ngày25/10, window
14:35–14:42 UTC, userIdentity.invokedBy=cloudformation.amazonaws.com:
provider còn dùng EC2 DescribeImages/DescribeKeyPairs và ASG CreateOrUpdateTags.
Đã tra SAR, bổ sung hai quyền metadata chỉ Singapore và tag đúng ASG chỉ qua
CloudFormation trong cùng policy/role đã duyệt. Access Analyzer0 finding,
thêm6 simulation allow/deny đạt (19 tổng); đọc lại live policy khớp file.
Không thêm GetSecretValue, key material, quyền RDS/S3/IAM/network, không mở
phạm vi ARN. Đây là Event History90 ngày; durable CloudTrail vẫn chưa có.

Repo tạo ngày26/09/2026; GitHub mặc định dùng subject có immutable IDs:
`repo:triramisu@200755440/shop@1389536014:environment:production`.
`aud` phải đúng `sts.amazonaws.com`; environment production bắt buộc chỉ
nhánh main. Không dùng subject legacy hoặc wildcard repo. Xem
[quy định GitHub](https://docs.github.com/en/actions/reference/security/oidc).

Ý nghĩa từng nhóm statement:

| Statement | Quyền/phạm vi |
| --- | --- |
| ReadProductionStack | Đọc template, stack, resources và plan của đúng production stack/plan CD |
| CreateNamedImagePlansWithoutServiceRole | Tạo UPDATE plan tên asg-cd-gh-*, không truyền service RoleArn |
| ExecuteNamedImagePlans | Execute đúng prefix plan trên stack này |
| CloudFormationTagDependencies | Tag/Untag được SAR liệt kê cho Create/Execute; chỉ stack/plan trên |
| RegionalReadOperationsWithoutResourceScoping | Các API đọc cần Resource=* theo AWS; giới hạn Singapore |
| ExistingLaunchTemplateViaCloudFormation | Version/tag của đúng LT, chỉ qua CloudFormation; có delete version phục vụ rollback |
| ExistingAsgNativeRefreshViaCloudFormation | Update/native refresh/cancel/scaling policy và đồng bộ tag của đúng ASG, chỉ qua CloudFormation |
| ValidatePinnedLaunchResourcesViaCloudFormation | RunInstances validation với đúng AMI/LT/key/SG/private subnets; volume/ENI mới giới hạn account/region/LT |
| ValidateAppInstancesViaCloudFormation | App t3.small, IMDSv2, đúng instance profile và LT |
| LaunchTimeTagsViaCloudFormation | CreateTags chỉ khi RunInstances qua CloudFormation |
| PassOnlyExistingAppRoleViaCloudFormation | PassRole đúng app role tới EC2 qua CloudFormation |
| PassExistingAutoScalingServiceRoleViaCloudFormation | PassRole service-linked role sẵn có tới Auto Scaling |
| RuntimeProbeDocument | SendCommand với AWS-RunShellScript |
| RuntimeProbeOnlyCurrentAppFleet | Chỉ EC2 có system tag aws:autoscaling:groupName khớp ASG này |

Nguồn quyền: thao tác AWS CLI của pipeline và dependency của CloudFormation
resource handlers; đã tra từng API trong Service Authorization Reference
ngày26/10/2026. Controller gọi CLI qua child_process, không có AWS SDK calls
để IAM Policy Autopilot phân tích. Không suy quyền CD từ Java S3 adapter.
[Service reference](https://docs.aws.amazon.com/service-authorization/latest/reference/reference_policies_actions-resources-contextkeys.html),
[ASG kiểm RunInstances/PassRole](https://docs.aws.amazon.com/autoscaling/ec2/userguide/ec2-auto-scaling-launch-template-permissions.html),
[CalledViaFirst](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_condition-keys.html#condition-keys-calledviafirst).

Access Analyzer: permissions policy không finding; trust có một suggestion
aud single-valued (đã dùng StringEquals, không qualifier) và một warning
khuyên thêm branch. Trust không có wildcard; branch được khóa bằng GitHub
environment deployment policy. Phải hậu kiểm policy main trước khi cho assume.
Chưa kiểm chứng end-to-end role permissions bằng một lần rollout thật.

Giới hạn bảo mật: IAM không diễn đạt “chỉ đổi ApplicationImage”; controller
và CI áp invariant đó. SendCommand AWS-RunShellScript cho phép chạy shell
với quyền root trên app fleet, mặc dù script của task chỉ đọc metadata.
Ai sửa được workflow đã được bảo vệ có quyền triển khai code lên app; vì
vậy cần giữ main protection, OIDC/environment scope và review source.
Role không cấp API đọc Secrets Manager hoặc quyền sửa RDS/S3/UI/IAM/network.
Guard34 finding cũ và durable CloudTrail chưa có vẫn là khoản tồn đọng.

## Thiết lập một lần

1. Giữ main protection: PR, verify/dependency-review, strict up-to-date,
   enforce admin, không force-push/xóa. CI merge commit phải xanh.
2. Chủ repo tạo Docker Hub token Read/Write và Store dưới repository secret
   `DOCKERHUB_TOKEN`. Không lưu token vào source, README hoặc shell history.
3. Review hai policy ở trên; validate-policy/simulation trước attach. Tạo
   role với OIDC trust này, MaxSessionDuration=5400, attach đúng một inline
   permissions policy. Không thêm AdministratorAccess để sửa lỗi quyền.
4. GitHub Settings -> Environments -> production -> Deployment branches:
   **Selected branches and tags**, chỉ branch `main`, không thêm tag wildcard.
5. Settings -> Secrets and variables -> Actions -> Variables:

| Variable | Value |
| --- | --- |
| DOCKERHUB_USERNAME | t1r4m15u |
| AWS_ACCOUNT_ID | 233377400170 |
| AWS_REGION | ap-southeast-1 |
| CFN_STACK_NAME | shop-aydibro-prod |
| AWS_DEPLOY_ROLE_ARN | arn:aws:iam::233377400170:role/shop-backend-github-deploy |
| ASG_CD_ENABLED | false trong lúc chuẩn bị; true để chạy manual trial |
| ASG_AUTO_DEPLOY_ENABLED | false tới khi trial rollout đạt |

Không cần AWS access key, EC2 ID hoặc copy application secret lên GitHub.
Máy app tiếp tục tự đọc Secrets Manager bằng instance role hiện hành.

## Chạy thử, hậu kiểm và bật tự động

Actions -> CD -> Run workflow -> Branch main:

1. `verify`, image=digest đang chạy: OIDC + đọc stack/fleet + probe 2 máy;
   không tạo change set. Report phải ALREADY_DEPLOYED, verifiedInstances=2.
2. `plan`, để trống image: build/scan/push source main đã qua CI và tạo preview,
   chưa Execute. Dùng report/digest để review. Sau duyệt, chạy `apply` với đúng
   digest đó; controller tạo plan mới và kiểm lại toàn bộ trước Execute.
   Plan phải chỉ 3 logical
   IDs trong hợp đồng; AWS validation không có finding. Controller kiểm lại
   stack/fleet ngay trước Execute và không tự bỏ qua guard khi thất bại.
3. Chờ stack UPDATE_COMPLETE và **refresh mới** Successful; LT tăng version,
   đúng digest trên toàn bộ ASG và target Healthy cả hai AZ. Job tối đa75
   phút; rollout có thể tạm chạy3 app instance, phát sinh phí. Không coi job
   đỏ/timeout hoặc chỉ một node Healthy là deploy thành công.
4. Tải artifact `asg-release-<run>-<attempt>` để lưu source SHA, old/new digest,
   plan ARN, template hash và số node verified. Không chứa runtime env.
5. Chỉ khi trial thật đạt mới đặt ASG_AUTO_DEPLOY_ENABLED=true. Lần merge sau
   tự đi theo luồng trên, không cần mở Termius. Nếu main đổi trong lúc build,
   run cũ dừng; run mới chờ concurrency, không hủy rollout đang thực hiện.

Nếu job thiếu lệnh DescribeEvents, phải cập nhật AWS CLI v2 của runner trước
thử lại; không bỏ bước validation. Verify và plan khác image không thay web;
apply mới Execute. Chạy khi lab ASG đang bật, ổn định, không chạy tải.

## Lỗi, dừng và rollback

- `TEMPLATE_DRIFT` hoặc plan ngoài phạm vi: dừng, so template Original với
  source; không cập nhật toàn bộ template chỉ để ép CD chạy.
- Lab đang tắt: bật theo runbook tiết kiệm, đợi cả fleet/targets Healthy rồi
  chạy lại. CD không tự tăng Desired để đánh thức lab.
- Probe trùng lúc scale-out/in: snapshot khác làm job fail; kiểm ASG và chạy
  verify lại. Không gửi deploy.sh tới instance vừa sinh để vá tạm.
- CloudFormation rollback: kiểm image cũ và toàn fleet; release vẫn đỏ để
  người vận hành biết bản mới thất bại. Rollback failed/timeout cần đọc stack
  events; không terminate, start refresh thứ hai hoặc continue rollback mù.
- Rollback image thủ công: tắt ASG_AUTO_DEPLOY_ENABLED, lấy previousImage từ
  artifact rồi Run CD/apply với digest cũ. Vẫn tạo image-only change set và
  refresh có health gate. Workflow rollback vẫn dùng source main hiện tại.
- Đổi image không hoàn tác dữ liệu/Flyway. Release có migration phải review
  tương thích/backup riêng; pipeline này không tự drop bảng hay restore RDS.
- Plan preview chưa Execute có thể giữ để điều tra; dọn bằng quyền quản trị
  sau khi kiểm ARN/status. Deployment role không có DeleteStack/DeleteChangeSet.

## Lệnh kiểm tra source không cần AWS

```bash
node --test ci/asg-release.test.cjs ci/asg-cli-contract.test.cjs ci/cd-source.test.cjs
actionlint .github/workflows/ci.yml .github/workflows/cd.yml
git diff --check
```

CI verify chạy thêm94 ca Node preflight/template/trusted-proxy và full Maven
verify, giữ nguyên hai required job names để branch protection tiếp tục có hiệu lực.

Các lệnh bootstrap IAM đã dùng, chạy từ gốc repo bằng Bash sau review/duyệt
(không lặp lại create-role nếu role đã tồn tại):

```bash
aws accessanalyzer validate-policy --profile shop-prod --region ap-southeast-1 \
  --policy-type IDENTITY_POLICY \
  --policy-document file://deploy/aws/iam/backend-github-permissions.json
aws iam create-role --profile shop-prod --region ap-southeast-1 \
  --role-name shop-backend-github-deploy --max-session-duration 5400 \
  --assume-role-policy-document file://deploy/aws/iam/backend-github-trust.json
aws iam put-role-policy --profile shop-prod --region ap-southeast-1 \
  --role-name shop-backend-github-deploy --policy-name BackendAsgImageRelease \
  --policy-document file://deploy/aws/iam/backend-github-permissions.json
aws iam get-role --profile shop-prod --region ap-southeast-1 \
  --role-name shop-backend-github-deploy \
  --query 'Role.{Arn:Arn,Duration:MaxSessionDuration}'
```

Nếu làm dự án khác, tra lại account/region, immutable repo/owner IDs, stack
ARN, LT/ASG/AMI/SG/subnet/profile/role trước khi dùng. Không thay mỗi tên repo
rồi áp nguyên bộ quyền này sang tài khoản khác. Khôi phục role bằng chính hai
file versioned sau review; không xóa/ghi đè OIDC provider đang dùng cho UI.
