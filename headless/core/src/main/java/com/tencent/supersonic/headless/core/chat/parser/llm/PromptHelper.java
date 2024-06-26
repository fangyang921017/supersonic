package com.tencent.supersonic.headless.core.chat.parser.llm;

import com.tencent.supersonic.headless.core.chat.query.llm.s2sql.LLMReq;
import com.tencent.supersonic.headless.core.chat.query.llm.s2sql.LLMReq.ElementValue;
import com.tencent.supersonic.headless.core.config.ParserConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.tencent.supersonic.headless.core.config.ParserConfig.PARSER_EXEMPLAR_RECALL_NUMBER;
import static com.tencent.supersonic.headless.core.config.ParserConfig.PARSER_FEW_SHOT_NUMBER;
import static com.tencent.supersonic.headless.core.config.ParserConfig.PARSER_SELF_CONSISTENCY_NUMBER;

@Component
@Slf4j
public class PromptHelper {

    @Autowired
    private ParserConfig parserConfig;

    @Autowired
    private ExemplarManager exemplarManager;

    public List<List<Map<String, String>>> getFewShotExemplars(LLMReq llmReq) {
        int exemplarRecallNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_EXEMPLAR_RECALL_NUMBER));
        int fewShotNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_FEW_SHOT_NUMBER));
        int selfConsistencyNumber = Integer.valueOf(parserConfig.getParameterValue(PARSER_SELF_CONSISTENCY_NUMBER));

        List<Map<String, String>> exemplars = exemplarManager.recallExemplars(llmReq.getQueryText(),
                exemplarRecallNumber);
        List<List<Map<String, String>>> results = new ArrayList<>();

        // use random collection of exemplars for each self-consistency inference
        for (int i = 0; i < selfConsistencyNumber; i++) {
            List<Map<String, String>> shuffledList = new ArrayList<>(exemplars);
            Collections.shuffle(shuffledList);
            results.add(shuffledList.subList(0, fewShotNumber));
        }

        return results;
    }

    public Pair<String, String> transformQuestionPrompt(LLMReq llmReq) {
        String tableName = llmReq.getSchema().getDataSetName();
        List<String> fieldNameList = llmReq.getSchema().getFieldNameList();
        List<LLMReq.ElementValue> linkedValues = llmReq.getLinking();
        String currentDate = llmReq.getCurrentDate();
        String priorExts = llmReq.getPriorExts();

        String dbSchema = "Table: " + tableName + ", Columns = " + fieldNameList;

        List<String> priorLinkingList = new ArrayList<>();
        for (ElementValue value : linkedValues) {
            String fieldName = value.getFieldName();
            String fieldValue = value.getFieldValue();
            priorLinkingList.add("‘" + fieldValue + "‘是一个‘" + fieldName + "‘");
        }
        String currentDataStr = "当前的日期是" + currentDate;
        String linkingListStr = String.join("，", priorLinkingList);
        String termStr = getTermStr(llmReq);
        String questionAugmented = String.format("%s (补充信息:%s;%s;%s;%s)", llmReq.getQueryText(),
                linkingListStr, currentDataStr, termStr, priorExts);

        return Pair.of(dbSchema, questionAugmented);
    }

    private String getTermStr(LLMReq llmReq) {
        List<LLMReq.Term> terms = llmReq.getSchema().getTerms();
        StringBuilder termsDesc = new StringBuilder();
        if (!CollectionUtils.isEmpty(terms)) {
            termsDesc.append("相关业务术语：");
            for (int idx = 0; idx < terms.size(); idx++) {
                LLMReq.Term term = terms.get(idx);
                String name = term.getName();
                String description = term.getDescription();
                List<String> alias = term.getAlias();
                String descPart = StringUtils.isBlank(description) ? "" : String.format("，它通常是指<%s>", description);
                String aliasPart = CollectionUtils.isEmpty(alias) ? "" : String.format("，类似的表达还有%s", alias);
                termsDesc.append(String.format("%d.<%s>是业务术语%s%s；", idx + 1, name, descPart, aliasPart));
            }
            if (termsDesc.length() > 0) {
                termsDesc.setLength(termsDesc.length() - 1);
            }
        }

        return termsDesc.toString();
    }

}
