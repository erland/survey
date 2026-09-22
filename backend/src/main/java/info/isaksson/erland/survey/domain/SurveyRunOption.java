package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "survey_run_option", uniqueConstraints = {
        @UniqueConstraint(name = "uq_survey_run_option_position", columnNames = {"run_question_id", "position"}),
        @UniqueConstraint(name = "uq_survey_run_option_value", columnNames = {"run_question_id", "value"})
})
public class SurveyRunOption extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "run_question_id") public SurveyRunQuestion question;
    @Column(name = "source_option_id") public UUID sourceOptionId;
    @Column(nullable = false) public int position;
    @Column(nullable = false, length = 200) public String value;
    @Column(nullable = false, length = 500) public String label;
}
