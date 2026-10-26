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
   quyền ghi giới hạn stack/fleet; DescribeEvents đọc Singapore được duyệt
   riêng. Từ chối stack/fleet bận, thiếu target, lab
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

Trial AWS CD-ASG-01 ngày26/10/2026 đã đạt qua run40325309982; chi tiết kết
quả ở cuối phần này. Bước bàn giao là required checks/CI merge commit xanh
rồi mới bật tự động theo quy trình bên dưới. Các biên bản trước phản ánh
trạng thái tại thời điểm chạy, không phải trạng thái hiện hành. PR47 đã merge tại
`132d4a9b87aae65fa5ce88450de075962111b558`; verify của merge commit xanh,
dependency-review skipped đúng điều kiện push. Docker Hub token đã được chủ
repo Store; chỉ kiểm tên `DOCKERHUB_TOKEN`, không đọc token. CD source qua
[PR48](https://github.com/triramisu/shop/pull/48), merge `1473871`; required
checks và CI merge commit xanh. Role riêng đã được duyệt/tạo. Manual gate
ASG_CD_ENABLED=true; ASG_AUTO_DEPLOY_ENABLED=false trong thời gian trial.
Trial đầu đã rollback; các đoạn dưới lưu bằng chứng theo thời điểm.

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

PR51 đã merge `20134b1`, CI main xanh. Hai lần OIDC40318776840/40319529977
vẫn bị DescribeEvents AccessDenied với full stack selector/ARN và thử prefix
tên stack. Đã khôi phục policy cũ sau thử nghiệm prefix không đạt. Chủ repo
sau đó duyệt riêng quyền đọc DescribeEvents Resource=* tại Singapore; quyền
này có thể xem metadata/cấu hình trong event của các stack khác tại region.
Không thêm quyền ghi hoặc GetSecretValue. Access Analyzer0 finding cho policy,
7 ca simulation bổ sung đạt, và đọc lại live policy khớp bản đã duyệt.

[Preview OIDC40320764004](https://github.com/triramisu/shop/actions/runs/40320764004)
**đã đạt** PLAN_ONLY_NOT_EXECUTED: đúng3 Modify LT/ASG/scaling policy, không
Add/Remove; LT replacementFalse, ASG/policyConditional; validation complete
với2 normal events, không warning/error. Hash template giữ319e79f5….
Image được promote theo digest9e8791cd… từ build/scan/push56d76a7; controller
chạy20134b1. Git diff src/main/pom/Dockerfile giữa hai commit rỗng; image
anonymous pull đã xác nhận public/linux-amd64. Chưa Execute/rollout image mới.

Preflight read-only19:17:33 UTC+7 ngày26/10: stack UPDATE_COMPLETE, hai AZ
app Healthy/LT8, AMI0080f2c6aa4e378b7 và revision checkout-loc-20261025-01;
secret AWSCURRENT version khớp baseline (chỉ đọc metadata, không plaintext).
Trial apply sẽ tạo/validate lại plan với cùng digest và boundary trước Execute;
không Execute preview cũ qua tài khoản admin. Chỉ bật auto sau khi native
refresh mới Successful và toàn fleet/targets/image đạt, kèm CI bàn giao.

[Trial apply40322255832](https://github.com/triramisu/shop/actions/runs/40322255832)
đã Execute plan mới đúng3 Modify, AWS validation đạt. Stack tự về
UPDATE_ROLLBACK_COMPLETE vì ApplicationLaunchTemplate không đọc được
Database.Endpoint.Address: thiếu rds:DescribeDBInstances trên đúng RDS.
Controller xác minh toàn fleet vẫn dùng image0ec7922b… trước khi báo release
FAILED_REVIEW_REQUIRED. LT8, hai instance cũ và hai target Healthy được giữ;
không start refresh/terminate thủ công để vượt lỗi.

Đã rà GetAtt/Outputs cùng CloudTrail của lần cập nhật thành công trước:
UserData đọc RDS endpoint, LT đọc instance-profile ARN; Outputs đọc RDS,
bastion PublicIp, ALB DNSName và CloudFront DomainName. Image-only không bỏ
những dependency này. Bản policy trong PR53 bổ sung12 read actions đã được
chủ repo duyệt; đã attach vào cùng inline policy và đọc lại khớp source.
Access Analyzer0 finding; 20/20 simulation đạt:12 allow qua CloudFormation,
5 từ chối gọi trực tiếp và3 từ chối quyền ghi RDS/IAM/CloudFront. Các15
statement cũ không đổi. Trial OIDC thực tế sau đó đạt; simulation riêng lẻ
không được dùng thay bằng chứng rollout.
RDS/profile/distribution/bastion khóa ARN; các Describe không hỗ trợ ARN chỉ
Singapore. Tất cả quyền đọc mới yêu cầu CalledViaFirst=CloudFormation.
GetDistribution trả cả origin config/custom headers; không xuất cấu hình này
ra log/artifact. Không thêm quyền ghi, GetSecretValue hoặc đăng nhập SQL.

[Trial lại40325309982](https://github.com/triramisu/shop/actions/runs/40325309982)
**SUCCESS / DEPLOYED_AND_FLEET_VERIFIED**, source controller20134b1, promote
đúng digest9e8791cd… từ build56d76a7. Plan
`asg-cd-gh-40325309982-1` chỉ3 Modify; validation complete/2 normal events.
Native refresh `664389a2-b7c1-47e6-b9cf-38410d1d7c5f` Successful, từ
19:49:32 tới20:21:26 UTC+7 ngày26/10,31 phút54 giây. Stack UPDATE_COMPLETE.
ASG tự tạo/thay tuần tự máy tại hai AZ; không manual refresh/terminate.

Artifact `asg-release-40325309982-1` ghi verifiedInstances=2, LT9. SSM probe
trên toàn bộ fleet động đạt: đúng image, Running/Health, restart0, không OOM.
Hậu kiểm20:22:34 UTC+7 xác nhận:

| Thuộc tính | Kết quả |
| --- | --- |
| App AZ A | i-004c4d27345010993, LT9, InService/Healthy |
| App AZ B | i-0bba5a69618a5e795, LT9, InService/Healthy |
| Capacity | Min2, Desired2, Max4; cả hai target Healthy |
| Image | docker.io/t1r4m15u/shop@sha256:9e8791cdead1554275c98458249d48cc1c26ebfee19b88d633881658f82073c9 |
| Giữ nguyên | AMI0080f2c6aa4e378b7, revision checkout-loc-20261025-01, secret AWSCURRENT version |
| Template | Hash319e79f5… như trước; mọi parameter ngoài ApplicationImage được controller đối chiếu không đổi |
| HTTP | / trả200; /api/catalog/products?page=0&size=1 trả200/totalElements12; /api/orders chưa đăng nhập trả401 |

Hai EC2 ID ở bảng là bằng chứng của trial, không phải biến cấu hình CD.
Trial kiểm chứng refresh2 máy, không phải load test/ép CPU scale-out tới4
máy, diễn tập restore RDS hoặc nghiệm thu go-live. AMI/schema V26, VNPay
sandbox, UI/DNS/WAF giữ nguyên; Guard34/durable CloudTrail vẫn còn.

Bàn giao qua PR53: giữ auto=false khi merge, chờ verify/dependency-review
trên đúng head và CI merge commit main xanh. Chờ CD workflow_run của merge
bàn giao kết thúc skipped, rồi đặt ASG_AUTO_DEPLOY_ENABLED=true và đọc lại
cả hai cờ. Cách này bật tự động cho release sau mà không phát hành thêm một
image chỉ vì merge tài liệu bàn giao. Quyền bật cờ sau nghiệm thu đã được
chủ repo duyệt. Trạng thái cờ thực tế xem tại GitHub Actions Variables.

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
| ReadValidationEventsInSingapore | DescribeEvents Resource=* chỉ Singapore, đã được chủ repo duyệt vì thử scoped ARN bị từ chối; controller vẫn khóa selector/StackId |
| ReadDatabaseReferenceViaCloudFormation | DescribeDBInstances đúng RDS để resolve endpoint; metadata, không SQL/password |
| ReadApplicationProfileReferenceViaCloudFormation | GetInstanceProfile đúng app profile để resolve ARN |
| ReadDistributionOutputViaCloudFormation | GetDistribution/ListTagsForResource đúng distribution để resolve Outputs; chứa origin config, chỉ qua CloudFormation |
| ReadBastionOutputViaCloudFormation | DescribeInstanceAttribute đúng bastion cho PublicIp Output |
| ReadRegionalProviderOutputsViaCloudFormation | Describe ALB/capacity/tags/attributes và EC2 credits/volumes/ENIs theo handler read; Resource=* chỉ Singapore, chỉ qua CloudFormation |

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
Role đã qua rollout OIDC thật40325309982, gồm native refresh và probe cả
fleet. Điều này không mở rộng quyền role hoặc thay nghiệm thu go-live.

Giới hạn bảo mật: IAM không diễn đạt “chỉ đổi ApplicationImage”; controller
và CI áp invariant đó. SendCommand AWS-RunShellScript cho phép chạy shell
với quyền root trên app fleet, mặc dù script của task chỉ đọc metadata.
Ai sửa được workflow đã được bảo vệ có quyền triển khai code lên app; vì
vậy cần giữ main protection, OIDC/environment scope và review source.
Role không cấp API đọc Secrets Manager hoặc quyền sửa RDS/S3/UI/IAM/network.
Riêng DescribeEvents có quyền đọc events các stack khác trong Singapore;
các event có thể chứa cấu hình resource. Không log ResourceProperties trong
controller/artifact. Main/environment/OIDC và quyền ghi stack/LT/ASG giữ scope.
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
   `sourceSha` là commit chạy controller/workflow; khi promote/rollback bằng
   input image, lưu thêm build commit/run riêng. Trial hiện dùng build56d76a7
   và controller20134b1, runtime diff src/main/pom/Dockerfile bằng0.
5. Chỉ khi trial thật đạt và CI bàn giao xong mới đặt ASG_AUTO_DEPLOY_ENABLED=true.
   Nếu merge PR ghi bằng chứng trial, giữ auto=false tới khi CI merge commit
   xanh và CD của merge đó đã skipped, rồi bật và đọc lại hai cờ trong Variables.
   Lần merge sau
   tự đi theo luồng trên, không cần mở Termius. Nếu main đổi trong lúc build,
   run cũ dừng; run mới chờ concurrency, không hủy rollout đang thực hiện.

Nếu job thiếu lệnh DescribeEvents, phải cập nhật AWS CLI v2 của runner trước
thử lại; không bỏ bước validation. Verify và plan khác image không thay web;
apply mới Execute. Chạy khi lab ASG đang bật, ổn định, không chạy tải.

## Lỗi, dừng và rollback

- Trong refresh có thể tạm có3 target: hai máy cũ Healthy và máy mới đang
  bootstrap. ASG HealthStatus=Healthy trong grace period chưa chứng minh app
  Healthy; kiểm cả TargetHealth và runtime. Không deregister máy mới, dừng
  máy cũ hoặc đổi health endpoint thành phản hồi giả để vượt bước chờ.
- Nếu máy mới chưa Healthy, kiểm trạng thái cloud-final.service và Docker
  metadata qua SSM. Chỉ lấy Running/Health/RestartCount/OOMKilled; không xuất
  toàn bộ docker inspect, env-file/UserData hoặc log thô. Trial26/10 ghi nhận
  bước keytool tạo truststore RDS trước khi container được tạo. Giữ grace20
  phút/warmup3 phút đã duyệt; timeout/failure phải điều tra, không ép refresh.
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

Đọc tiến trình từ máy quản trị, Bash (không thay đổi AWS):

```bash
aws cloudformation describe-stacks --profile shop-prod --region ap-southeast-1 \
  --stack-name shop-aydibro-prod --query 'Stacks[0].StackStatus'
asg_name=$(aws cloudformation describe-stack-resource --profile shop-prod \
  --region ap-southeast-1 --stack-name shop-aydibro-prod \
  --logical-resource-id ApplicationAutoScalingGroup \
  --query 'StackResourceDetail.PhysicalResourceId' --output text)
aws autoscaling describe-instance-refreshes --profile shop-prod \
  --region ap-southeast-1 --auto-scaling-group-name "$asg_name" --max-records 5 \
  --query 'InstanceRefreshes[].{Id:InstanceRefreshId,Status:Status,Progress:PercentageComplete,Reason:StatusReason}'
target_group=$(aws cloudformation describe-stack-resource --profile shop-prod \
  --region ap-southeast-1 --stack-name shop-aydibro-prod \
  --logical-resource-id ApplicationTargetGroup \
  --query 'StackResourceDetail.PhysicalResourceId' --output text)
aws elbv2 describe-target-health --profile shop-prod --region ap-southeast-1 \
  --target-group-arn "$target_group" \
  --query 'TargetHealthDescriptions[].{Id:Target.Id,State:TargetHealth.State,Reason:TargetHealth.Reason}'
```

Các lệnh này dành cho profile quản trị, không thêm quyền
DescribeStackResource riêng cho deployment role chỉ để chạy lệnh hướng dẫn.

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
