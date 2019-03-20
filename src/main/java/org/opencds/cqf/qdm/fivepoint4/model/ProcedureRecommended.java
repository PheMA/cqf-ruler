package org.opencds.cqf.qdm.fivepoint4.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import java.io.Serializable;

@MappedSuperclass
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public abstract class ProcedureRecommended extends BaseType implements Serializable
{
    private String authorDatetime;

    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "reason_code")),
            @AttributeOverride(name = "display", column = @Column(name = "reason_display")),
            @AttributeOverride(name = "system", column = @Column(name = "reason_system")),
            @AttributeOverride(name = "version", column = @Column(name = "reason_version"))
    })
    private Code reason;

    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "anatomical_location_site_code")),
            @AttributeOverride(name = "display", column = @Column(name = "anatomical_location_site_display")),
            @AttributeOverride(name = "system", column = @Column(name = "anatomical_location_site_system")),
            @AttributeOverride(name = "version", column = @Column(name = "anatomical_location_site_version"))
    })
    private Code anatomicalLocationSite;

    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "ordinality_code")),
            @AttributeOverride(name = "display", column = @Column(name = "ordinality_display")),
            @AttributeOverride(name = "system", column = @Column(name = "ordinality_system")),
            @AttributeOverride(name = "version", column = @Column(name = "ordinality_version"))
    })
    private Code ordinality;

    @AttributeOverrides({
            @AttributeOverride(name = "code", column = @Column(name = "negation_rationale_code")),
            @AttributeOverride(name = "display", column = @Column(name = "negation_rationale_display")),
            @AttributeOverride(name = "system", column = @Column(name = "negation_rationale_system")),
            @AttributeOverride(name = "version", column = @Column(name = "negation_rationale_version"))
    })
    private Code negationRationale;

    @Override
    public void copy(BaseType other)
    {
        if (other instanceof ProcedureRecommended)
        {
            ProcedureRecommended procedureRecommended = (ProcedureRecommended) other;
            super.copy(procedureRecommended);
            setAuthorDatetime(procedureRecommended.getAuthorDatetime());
            setReason(procedureRecommended.getReason());
            setAnatomicalLocationSite(procedureRecommended.getAnatomicalLocationSite());
            setOrdinality(procedureRecommended.getOrdinality());
            setNegationRationale(procedureRecommended.getNegationRationale());
        }
        else
        {
            throw new IllegalArgumentException(
                    String.format("Cannot copy QDM types %s and %s", this.getClass().getName(), other.getClass().getName())
            );
        }
    }
}
