# FOFA 5.0 API 文档

> 本文档整理自 FOFA 5.0 官方 API 文档
> 整理日期: 2026-02-06

## 目录

- [API 简介](#api-简介)
- [请求结构](#请求结构)
- [使用限制](#使用限制)
- [查询接口](#查询接口)
- [Host 聚合](#host-聚合)
- [统计聚合](#统计聚合)
- [连续翻页接口](#连续翻页接口)
- [账号信息](#账号信息)

---

## API 简介

使⽤限制 FOFA 是⼀个⽹络空间测绘平台。它将采集全球互联⽹的数据，允许您和您的团队随时随
地取⽤数据。
请求结构 此⽂档意在辅助⽤户开始利⽤ FOFA API 构建、寻找、维护⾃⼰的软件。了解 $，注册⼀
个账户，与⽹络安全前沿社区建⽴联系。
通过 FOFA API ，您⾄少可以完成：
1. 监控暴露⾯资产变化情况
2. 持续发现遗漏的互联⽹资产
统计聚合 3. 对开放的互联⽹资产进⾏画像和⻛险判断
4. 快速响应突发安全事件
5. 对外部安全事件及资产的拓线关联
6. 完成SRC前期的信息收集，中期的信息分析聚焦，后期的⻛险画像，扩⼤战果



---

## 请求结构

查询接⼝ 服务地址：接⼝接⼊域名 fofa.info
通信协议：所有接⼝均通过 HTTPS 进⾏通信，提供⾼安全性的通信通道
请求⽅法：⽀持 GET 的 HTTP 请求
字符编码：均使⽤ UTF-8 编码



---

## 使用限制

使⽤限制 本⽂档包含 FOFA 的基本知识，如查询、导出、聚合等。 您即将拥有⾃⼰的 FOFA 数据
⼯具。
请求结构 1. 要完成⾃⼰的⼯具，您需要FOFA帐户和连接互联⽹。涉及输⼊的信息，需要包含您的
账号信息，主要是 email 和 key。
查询接⼝ 2. 为了达到最好的效果，个⼈账号调试或调⽤时，请求速率<2/s 的情况下，成功率为
99.8%。
聚合接⼝统计 3. FOFA 账户中有您的私⼈信息，如头像、email、key、历史查询等，为了避免信息泄露
⻛险，请勿多⼈共⽤同⼀账户，平台将不定期清理共享账户，做终身回收处理。详⻅ 《⽤
统计聚合 户服务协议》
4. 统计聚合和Host聚合接⼝，不同的会员等级有不同的访问限制，具体详⻅ VIP介绍⻚
Host聚合 ⾯。



---

## 查询接口

使⽤限制 提供搜索主机、获取详细信息的⽅法，使开发更容易。
序号 参数 必填 类型 描述
1 qbase64 是 string 经过base64编码后
2 fields 否 string 可选字段，默认ho
3 page 否 int 是否翻⻚，默认为
4 size 否 int 每⻚查询数量，默
默认搜索⼀年内的
5 full 否 boolean
据
6 r_type 否 string 可以指定返回json格
附录1：查询接⼝⽀持的字段，按照示例配置 fields=ip,host,port 即可。
序号 字段名 描述


1 ip ip地址
2 port 端⼝
3 protocol 协议名
4 country 国家代码
5 country_name 国家名
请求结构 6 region 区域
查询接⼝ 7 city 城市
8 longitude 地理位置 经度
9 latitude 地理位置 纬度
10 asn asn编号
11 org asn组织
12 host 主机名
13 domain 域名
14 os 操作系统
15 server ⽹站server
16 icp icp备案号
17 title ⽹站标题
18 jarm jarm 指纹
19 header *1 ⽹站header

20 banner *1 协议 banner

21 cert *1 证书
22 base_protocol 基础协议，⽐如tcp/ud
23 link 资产的URL链接
24 cert.issuer.org 证书颁发者组织
25 cert.issuer.cn 证书颁发者通⽤名称
26 cert.subject.org 证书持有者组织
27 cert.subject.cn 证书持有者通⽤名称
28 tls.ja3s ja3s指纹信息
29 tls.version tls协议版本
30 cert.sn New 证书的序列号
31 cert.not_before 证书⽣效时间
32 cert.not_after 证书到期时间
33 cert.domain 证书中的根域名
34 header_hash http/https相应信息计算
35 banner_hash New 协议相应信息的完整ha
36 banner_fid New 协议相应信息架构的指纹
37 cname 域名cname
38 lastupdatetime FOFA最后更新时间
39 product 产品名
40 product_category 产品分类
41 product.version Beta 产品版本号

42 icon_hash 返回的icon_hash值

43 cert.is_valid 证书是否有效
44 cname_domain cname的域名
45 body ⽹站正⽂内容
46 cert.is_match 证书颁发者和持有者是否
47 cert.is_equal New 证书和域名是否匹配
48 icon icon 图标
49 fid fid
结构化信息 (部分协议⽀
50 structinfo
mongodb)
*1： 当查询包含cert、banner ，size参数值最⼤为2000
聚合接⼝统计 *2： 当查询包含body, size参数值最⼤为500
统计聚合 FOFA API⽀持cURL、Python、Java、Go语⾔的请求，以cURL为例：
curl
响应示例：
{
"error": false, // 是否出现错误
"consumed_fpoint": 0, // 应扣F点
"required_fpoints": 0, // 实扣F点
"size": 244569, // 查询总数量
"page": 1, // 当前⻚码
"mode": "extended",
"query": "title=\"bing\"", // 查询语句 new
"results": [
[

"https://bingchillin.org",
"172.67.213.134",

"443"
],
[
"bingchillin.org",
"104.21.69.223",
"80"
],
[
"76.158.236.234:8080",
"76.158.236.234",
"8080"
使⽤限制 ],
[
请求结构 "srkpixelsoft.com",
"43.225.55.146",
"80"
],
[
聚合接⼝统计 "https://srkpixelsoft.com",
"43.225.55.146",
统计聚合 "443"
],
Host聚合 [
"www.srkpixelsoft.com",
"43.225.55.146",
"80"
],
[
"https://www.srkpixelsoft.com",
"43.225.55.146",
"443"
连续翻⻚接⼝ ],
[
"3.10.194.226",
"3.10.194.226",
"80"
],
[
"3.104.212.139",
"3.104.212.139",
"80"
] 
]
} 



---

## Host 聚合

使⽤限制 根据当前的查询内容，⽣成聚合信息，host通常是ip，包含基础信息和IP标签。该接⼝限
制请求并发为 1s/次。
聚合接⼝统计 序号 参数 必填 类型 描述
1 host 是 string host名，
2 detail 否 boolean 显示端⼝
当detail=false时，默认为普通模式，以cURL为例：
curl
响应示例：
curl
{
"error": false,
"host": "78.48.50.249", new
"ip": "78.48.50.249",
"consumed_fpoint": 0, // 实际F点

"required_fpoints": 0, // 应付F点
"asn": 6805,

"org": "Telefonica Germany",
"country_name": "Germany",
"country_code": "DE",
"protocol": [
"sip",
"http",
"https"
],
"port": [
500,
443,
使⽤限制 80,
7170,
请求结构 5060,
8089
],
"category": [
"service",
聚合接⼝统计 "operating system"
],
统计聚合 "product": [
"gSOAP",
Host聚合 "FRITZ!OS"
],
"update_time": "2023-08-23 02:00:00"
}
返回结果字段:
字段名 描述
port 端⼝列表
protocol 协议列表
domain 域名列表
category 分类标签
product 产品标签


当detail=true时，默认为详情模式，以cURL为例：
curl
响应示例：
curl
{
"error": false,
"host": "78.48.50.249",
请求结构 "ip": "78.48.50.249",
"asn": 6805,
"org": "Telefonica Germany",
"country_name": "Germany",
"country_code": "DE",
"ports": [
{
"port": 8089,
"protocol": "http"
},
{
基础接⼝ "port": 7170,
"protocol": "http"
},
{
"port": 443,
"protocol": "https",
"products": [
{
"product": "Synology-WebStation",
"category": "Content Management System (CMS)
"level": 5,
"sort_hard_code": 2
}
]
}, new
{
"port": 5060,

"protocol": "sip"
}

],
"update_time": "2023-05-24 12:00:00"
}
返回结果字段:
字段名 描述
products 产品详情列表
product 产品名
category 产品分类
level 产品分层： 5 应⽤层， 4 ⽀持层， 3 服
聚合接⼝统计 soft_hard_code 产品是否为硬件；值为 1 是硬件，否则为



---

## 统计聚合

使⽤限制 根据当前的查询内容，⽣成全球统计信息，当前可统计每个字段的前5排名。该接⼝限制
请求并发为 5秒/次。
聚合接⼝统计 序号 参数 必填 类型 描述
统计聚合 经过bas
1 qbase64 是 string
内容
2 fields 否 string
附录2：统计聚合接⼝⽀持的字段，按照示例配置 fields=protocol,domain,port 即可。
序号 字段名 描述


1 protocol 协议
2 domain 域名
3 port 端⼝
4 title http 标题
5 os 操作系统
请求结构 6 server http server信息
查询接⼝ 7 country 国家、城市统计
8 asn asn编号
9 org asn组织
10 asset_type 资产类型
11 fid fid 统计
12 icp icp备案信息
返回结果字段:
连续翻⻚接⼝ 字段名 描述
distinct 唯⼀计数 ⽀持字段: server, icp, domai
aggs 聚合信息
FOFA API⽀持cURL、Python、Java、Go语⾔的请求，以cURL为例：
curl


响应示例：
{
"error": false, // 是否出现错误
"consumed_fpoint": 0, // 实际F点
"required_fpoints": 0, // 应付F点
"size": 4277422, // 查询总数量
"distinct": {
"ip": 32933,
"title": 82280
请求结构 },
"aggs": {
"title": [
{
"count": 76234,
聚合接⼝统计 "name": "⽹站未备案或已被封禁——百度智能云云主机管
},
统计聚合 {
"count": 50220,
Host聚合 "name": "百度⼀下, 你就知道"
},
{
"count": 39532,
"name": "百度热榜"
},
{
"count": 37177,
"name": "百度 H5 - 真正免费的 H5 ⻚⾯制作平台"
连续翻⻚接⼝ },
{
"count": 33986,
"name": "百度SEO"
}
]
},
"lastupdatetime": "2022-05-23 15:00:00"
}



---

## 连续翻页接口

使⽤限制 当针对同⼀搜索语句进⾏⼤规模数据获取时，为了避免使⽤⻚码翻⻚导致数据错位的问
题，推出连续翻⻚接⼝，可以持续获取所有数据，⽀持失败重试，⽆需担⼼数据错位。
聚合接⼝统计 序号 参数 必填 类型 描述
1 qbase64 是 string 经过base64编码后
2 fields 否 string 可选字段，默认ho
3 size 否 int 每⻚查询数量，默
翻⻚id ,每次响应结
4 next 否 string
默认返回第⼀⻚数
专⽤接⼝ 默认搜索⼀年内的
5 full 否 boolean
据
6 r_type 否 string 可以指定返回json格
附录1：查询接⼝⽀持的字段，按照示例配置 fields=ip,host,port 即可。
序号 字段名 描述


1 ip ip地址
2 port 端⼝
3 protocol 协议名
4 country 国家代码
5 country_name 国家名
请求结构 6 region 区域
查询接⼝ 7 city 城市
8 longitude 地理位置 经度
9 latitude 地理位置 纬度
10 asn asn编号
11 org asn组织
12 host 主机名
13 domain 域名
14 os 操作系统
15 server ⽹站server
16 icp icp备案号
17 title ⽹站标题
18 jarm jarm 指纹
19 header *1 ⽹站header

20 banner *1 协议 banner

21 cert *1 证书
22 base_protocol 基础协议，⽐如tcp/ud
23 link 资产的URL链接
24 cert.issuer.org 证书颁发者组织
25 cert.issuer.cn 证书颁发者通⽤名称
26 cert.subject.org 证书持有者组织
27 cert.subject.cn 证书持有者通⽤名称
28 tls.ja3s ja3s指纹信息
查询接⼝ 29 tls.version tls协议版本
30 cert.sn 证书的序列号
31 cert.not_before 证书⽣效时间
32 cert.not_after 证书到期时间
33 cert.domain 证书中的根域名
34 header_hash http/https相应信息计算
35 banner_hash 协议相应信息的完整ha
36 banner_fid 协议相应信息架构的指纹
37 cname 域名cname
38 lastupdatetime FOFA最后更新时间
39 product 产品名
40 product_category 产品分类
41 product.version 产品版本号

42 icon_hash 返回的icon_hash值

43 cert.is_valid 证书是否有效
44 cname_domain cname的域名
45 body ⽹站正⽂内容
46 cert.is_match 证书颁发者和持有者是否
47 cert.is_equal 证书和域名是否匹配
48 icon icon 图标
49 fid fid
结构化信息 (部分协议⽀
50 structinfo
mongodb)
*1： 当查询包含cert、banner ，size参数值最⼤为2000
聚合接⼝统计 *2： 当查询包含body, size参数值最⼤为500
统计聚合 FOFA API⽀持cURL、Python、Java、Go语⾔的请求，以cURL为例：
curl
响应示例：
{
"error": false, // 是否出现错误
"size": 163681, // 查询总数量
"page": 1, // 当前⻚码
"consumed_fpoint": 0, // 实际F点
"required_fpoints": 0, // 应付F点
"mode": "extended",
"next": "yzWlGs8GPJwdxnrDg3h4otdpNZMYF3un4FRjJnGbnknexw
"query": "title=\"百度\"", // 查询语句
"results": [

[
"737.syyycp.net",

"172.67.148.70",
"80"
],
[
"https://8754570.huicigen.com",
"172.67.208.21",
"443"
],
[
"https://41.syyycp.net",
"104.21.29.12",
使⽤限制 "443"
],
请求结构 [
"9677757.syyycp.net",
"104.21.29.12",
"80"
],
聚合接⼝统计 [
"https://512lbbbv.qimiduo.com",
统计聚合 "104.21.50.195",
"443"
Host聚合 ],
[
"https://583217806.huifengqing.com",
"104.21.40.29",
"443"
],
[
"https://447ziput.qimiduo.com",
"104.21.50.195",
连续翻⻚接⼝ "443"
],
[
"https://971061.yhitzz.com",
"104.21.72.192",
"443"
],
[
"https://58450830.qimiduo.com",
"104.21.50.195",
"443" 
],
[ 
"https://53551.yhitzz.com",
"104.21.72.192",
"443"
]
]
}



---

## 账号信息

使⽤限制 可以查看当前账号的状态、email、⽤户名、余额、会员等级等基础信息。注：没有查询数
据。
聚合接⼝统计 序号 参数 必填 类型 描述
1 key 是 string 个⼈中⼼的key，A
FOFA API⽀持cURL、Python、Java、Go的请求，以cURL为例：
curl
响应示例：
curl
{
"error": false, // 是否出现错误
"email": "fo****t@baimaohui.net", // 邮箱地址：
"username": "fofabot", // ⽤户名
"category": "user", // ⽤户种类 new
"fcoin": 0, // F币
"fofa_point": 49200, // page.userInfo.personCenter.F

"remain_free_point": 0, // 剩余免费F点
"remain_api_query": 49992, // API⽉度剩余查询次数

"remain_api_data": 499398, // API⽉度剩余返回数量
"isvip": true, // 是否是会员
"vip_level": 12, // 会员等级
"is_verified": false,
"avatar": "https://nosec.org/missing.jpg",
"message": "",
"fofacli_ver": "4.0.3",
"fofa_server": true
}



---

