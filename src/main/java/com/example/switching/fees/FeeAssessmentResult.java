package com.example.switching.fees;
import java.math.BigDecimal;import java.util.List;import java.util.UUID;import com.example.switching.promotion.dto.PromotionApplicationView;
public record FeeAssessmentResult(UUID tariffVersionId,UUID tariffRuleId,BigDecimal grossFee,BigDecimal promotionDiscount,
        BigDecimal netFee,String currency,List<PromotionApplicationView> promotions){
    public BigDecimal fee(){return netFee;}
    public static FeeAssessmentResult withoutPromotions(UUID version,UUID rule,BigDecimal fee,String currency){return new FeeAssessmentResult(version,rule,fee,BigDecimal.ZERO,fee,currency,List.of());}
}
