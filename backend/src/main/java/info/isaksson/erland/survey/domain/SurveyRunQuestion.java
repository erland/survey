package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.*;

@Entity
@Table(name = "survey_run_question", uniqueConstraints =
        @UniqueConstraint(name = "uq_survey_run_question_position", columnNames = {"run_id", "position"}))
public class SurveyRunQuestion extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_id") public SurveyRun run;
    @Column(name = "source_question_id") public UUID sourceQuestionId;
    @Column(nullable = false) public int position;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) public QuestionType type;
    @Column(nullable = false, columnDefinition = "text") public String text;
    @Column(nullable = false) public boolean required;
    @Column(name = "scale_min") public Integer scaleMin;
    @Column(name = "scale_max") public Integer scaleMax;
    @Column(name = "scale_min_label", length = 200) public String scaleMinLabel;
    @Column(name = "scale_max_label", length = 200) public String scaleMaxLabel;
    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC") public List<SurveyRunOption> options = new ArrayList<>();
}
