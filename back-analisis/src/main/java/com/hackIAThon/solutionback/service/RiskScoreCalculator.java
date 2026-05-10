package com.hackIAThon.solutionback.service;

import com.hackIAThon.solutionback.dto.ScoreBreakdownDto;
import com.hackIAThon.solutionback.entity.Finding;
import com.hackIAThon.solutionback.entity.FindingType;
import com.hackIAThon.solutionback.repository.FindingRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class RiskScoreCalculator {

    private final FindingRepository findingRepository;

    public RiskScoreCalculator(FindingRepository findingRepository) {
        this.findingRepository = findingRepository;
    }

    public ScoreBreakdownDto calculate(Long invoiceId) {
        List<Finding> findings = findingRepository.findByInvoiceId(invoiceId);

        int discrepanciesCount = 0;
        int duplicatesCount = 0;
        int unjustifiedCount = 0;
        int discrepanciesPenalty = 0;

        for (Finding f : findings) {
            if (f.getType() == FindingType.PRICE_EXCEEDED) {
                discrepanciesCount++;
                BigDecimal delta = f.getDeltaPercentual();
                if (delta != null) {
                    // -(delta/10) rounded down, minimum -1 (floor so even small % costs at least -1)
                    int rawPenalty = delta.divide(BigDecimal.TEN, 0, RoundingMode.FLOOR).intValue();
                    discrepanciesPenalty += Math.min(-1, -rawPenalty);
                } else {
                    discrepanciesPenalty += -1;
                }
            } else if (f.getType() == FindingType.DUPLICATE) {
                duplicatesCount++;
            } else if (f.getType() == FindingType.UNJUSTIFIED) {
                unjustifiedCount++;
            }
        }

        int duplicatesPenalty = duplicatesCount * -20;
        int unjustifiedPenalty = unjustifiedCount * -15;
        int finalScore = Math.max(0, 100 + discrepanciesPenalty + duplicatesPenalty + unjustifiedPenalty);

        return new ScoreBreakdownDto(
                100,
                discrepanciesPenalty,
                duplicatesPenalty,
                unjustifiedPenalty,
                finalScore,
                discrepanciesCount,
                duplicatesCount,
                unjustifiedCount
        );
    }
}
