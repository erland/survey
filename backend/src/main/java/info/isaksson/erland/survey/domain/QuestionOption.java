package info.isaksson.erland.survey.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name="question_option", uniqueConstraints={
    @UniqueConstraint(name="uq_question_option_position", columnNames={"question_id","position"}),
    @UniqueConstraint(name="uq_question_option_value", columnNames={"question_id","value"})
})
public class QuestionOption extends PanacheEntityBase {
    @Id public UUID id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="question_id") public SurveyQuestion question;
    @Column(nullable=false) public int position;
    @Column(nullable=false, length=200) public String value;
    @Column(nullable=false, length=500) public String label;
}
