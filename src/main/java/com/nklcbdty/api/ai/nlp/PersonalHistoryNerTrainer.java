package com.nklcbdty.api.ai.nlp;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.NameSampleDataStream;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.InputStreamFactory;
import opennlp.tools.util.MarkableFileInputStreamFactory;
import opennlp.tools.util.ObjectStream;
import opennlp.tools.util.PlainTextByLineStream;
import opennlp.tools.util.TrainingParameters;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 채용공고 경력 연차 추출용 NER 모델 학습기.
 *
 * <p>실행 방법 (IDE에서 main 실행 또는 Gradle):</p>
 * <pre>
 *   java -cp ... com.nklcbdty.api.ai.nlp.PersonalHistoryNerTrainer
 * </pre>
 *
 * <p>입력: {@code src/main/resources/nlp/personal_history_train.txt}</p>
 * <p>출력: {@code src/main/resources/nlp/personal_history_ner.bin}</p>
 *
 * <p>학습이 끝나면 {@link PersonalHistoryNerService}가 자동으로 로드합니다.
 * 학습 데이터(.txt)에 라벨을 추가할수록 모델 정확도가 올라갑니다.</p>
 */
public class PersonalHistoryNerTrainer {

    private static final String DEFAULT_TRAIN_PATH = "src/main/resources/nlp/personal_history_train.txt";
    private static final String DEFAULT_MODEL_PATH = "src/main/resources/nlp/personal_history_ner.bin";

    public static void main(String[] args) throws IOException {
        File trainFile = new File(args.length > 0 ? args[0] : DEFAULT_TRAIN_PATH);
        File modelFile = new File(args.length > 1 ? args[1] : DEFAULT_MODEL_PATH);

        if (!trainFile.exists()) {
            System.err.println("학습 데이터 파일을 찾을 수 없습니다: " + trainFile.getAbsolutePath());
            System.exit(1);
        }

        System.out.println("=== NER 학습 시작 ===");
        System.out.println("학습 데이터: " + trainFile.getAbsolutePath());

        InputStreamFactory isf = new MarkableFileInputStreamFactory(trainFile);
        try (ObjectStream<String> lines = new PlainTextByLineStream(isf, StandardCharsets.UTF_8);
             ObjectStream<NameSample> samples = new NameSampleDataStream(lines)) {

            TrainingParameters params = new TrainingParameters();
            params.put(TrainingParameters.ITERATIONS_PARAM, "200");
            params.put(TrainingParameters.CUTOFF_PARAM, "1");

            TokenNameFinderModel model = NameFinderME.train(
                    "ko", null, samples, params, new TokenNameFinderFactory());

            File parent = modelFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(modelFile))) {
                model.serialize(out);
            }

            System.out.println("=== 학습 완료 ===");
            System.out.println("모델 저장: " + modelFile.getAbsolutePath());
            System.out.println("이제 Spring Boot 재시작 시 PersonalHistoryNerService가 자동 로드합니다.");
        }
    }
}
