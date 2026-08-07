package com.chatbi.copilot.text2sql.service;

import com.chatbi.copilot.text2sql.dto.HistoryTurn;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class ClarificationPolicy {
    private static final Pattern EXPLICIT_TIME = Pattern.compile(
            "(?i)(20\\d{2}([-/年]\\d{1,2})?|\\b\\d+\\s*(day|week|month|year)s?\\b|"
                    + "近\\s*\\d+\\s*(天|周|个月|月|年)|本周|上周|本月|上月|本季度|上季度|今年|去年|昨天|今天)");
    private static final Pattern METRIC = Pattern.compile(
            "(?i)(销售额|收入|金额|订单|客户|用户|商品|产品|数量|人数|客单价|转化|退款|"
                    + "sales|revenue|amount|order|customer|user|product|count|average|total|refund)");
    private static final Pattern UNSUPPORTED = Pattern.compile(
            "(?i)(删除|修改|写入|建表|删表|密码|口令|密钥|绕过权限|忽略.*指令|"
                    + "\\b(delete|update|insert|drop|alter|truncate|password|secret|bypass|ignore previous)\\b)");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?i)(^(它|其|这些|那些|这个|那个|上面|刚才)|\\b(it|that|those|them|above|previous)\\b)");
    private static final Pattern FILTER_ONLY = Pattern.compile(
            "(?i)^(只看|仅看|只要|筛选|过滤|only|filter|show only)");
    private static final Pattern VAGUE_TIME = Pattern.compile(
            "(?i)(最近|近期|近来|当前|最新|趋势|走势|recent|recently|latest|trend)");
    private static final Pattern VAGUE_REQUEST = Pattern.compile(
            "(?i)^(分析一下|看一下|情况如何|表现怎么样|怎么样|概览|总览|"
                    + "analy[sz]e|overview|how is .* doing)[？?。.! ]*$");
    private static final Pattern TIME_GRAIN = Pattern.compile(
            "(?i)(按(日|周|月|季度|年)|每(日|周|月|季度|年)|daily|weekly|monthly|quarterly|yearly)");
    private static final Pattern DIMENSION_CUE = Pattern.compile("(?i)(分别|各(?!有)|按.+统计|breakdown|\\bby\\b)");
    private static final Pattern MULTIPLE_METRICS = Pattern.compile(
            "(?i)(订单数量|订单数|销售额|收入|金额|客户数|用户数|商品数|产品数|数量|人数|客单价|转化率|退款数|"
                    + "sales|revenue|amount|orders?|customers?|users?|products?|count|average|total|refund)"
                    + ".{0,4}(和|及|与|、|and).{0,4}"
                    + "(订单数量|订单数|销售额|收入|金额|客户数|用户数|商品数|产品数|数量|人数|客单价|转化率|退款数|"
                    + "sales|revenue|amount|orders?|customers?|users?|products?|count|average|total|refund)");
    private static final Pattern DIMENSION = Pattern.compile(
            "(?i)(渠道|状态|大区|地区|区域|城市|类目|类别|产品|商品|客户|用户|等级|日期|月份|季度|年度|按(日|周|月|季度|年)|"
                    + "channel|status|region|area|city|category|product|customer|user|level|date|month|quarter|year)");

    public Optional<ClarificationDecision> evaluate(String question, List<HistoryTurn> history) {
        String text = question == null ? "" : question.trim();
        String normalized = text.toLowerCase(Locale.ROOT);
        boolean hasHistory = history != null && !history.isEmpty();
        boolean chinese = text.codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);

        if (UNSUPPORTED.matcher(normalized).find()) {
            return Optional.of(new ClarificationDecision("UNSUPPORTED_OPERATION",
                    chinese ? "仅支持基于已授权数据的只读查询，不能执行写入、读取凭据或绕过权限。"
                            : "Only authorized read-only data questions are supported; writes, credential access, and permission bypasses are rejected."));
        }
        if (!hasHistory && REFERENCE.matcher(normalized).find()) {
            return Optional.of(new ClarificationDecision("MISSING_CONTEXT",
                    chinese ? "当前会话没有可引用的上一轮结果，请说明要查询的指标、维度和范围。"
                            : "This session has no prior result to reference. Specify the metric, dimension, and range."));
        }
        if (!hasHistory && FILTER_ONLY.matcher(normalized).find() && !METRIC.matcher(normalized).find()) {
            return Optional.of(new ClarificationDecision("MISSING_METRIC",
                    chinese ? "请说明要在该筛选条件下查看哪个指标，例如销售额、订单数或客户数。"
                            : "Specify which metric to show for that filter, such as sales, order count, or customer count."));
        }
        if ((VAGUE_TIME.matcher(normalized).find() || TIME_GRAIN.matcher(normalized).find())
                && !EXPLICIT_TIME.matcher(normalized).find()) {
            return Optional.of(new ClarificationDecision("AMBIGUOUS_TIME_RANGE",
                    chinese ? "请给出明确时间范围，例如近 30 天、2024 年或本月。"
                            : "Specify an exact time range, such as the last 30 days, 2024, or this month."));
        }
        boolean listsMultipleMetrics = MULTIPLE_METRICS.matcher(normalized).find();
        if (!hasHistory && DIMENSION_CUE.matcher(normalized).find() && !DIMENSION.matcher(normalized).find()
                && !listsMultipleMetrics) {
            return Optional.of(new ClarificationDecision("MISSING_DIMENSION",
                    chinese ? "请说明要按哪个维度分组，例如大区、渠道、状态或月份。"
                            : "Specify the grouping dimension, such as region, channel, status, or month."));
        }
        if (VAGUE_REQUEST.matcher(normalized).matches() || !hasHistory
                && !METRIC.matcher(normalized).find()) {
            return Optional.of(new ClarificationDecision("AMBIGUOUS_METRIC",
                    chinese ? "请说明要分析的指标、分组维度和时间范围。"
                            : "Specify the metric, grouping dimension, and time range to analyze."));
        }
        return Optional.empty();
    }
}
