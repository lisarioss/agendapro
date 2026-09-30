package com.lisarios.agendapro.catalog;

import com.lisarios.agendapro.common.Db;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
public class ProfessionalStore {
    private final Db db;
    public ProfessionalStore(Db db) { this.db=db; }
    /** Chamar dentro de uma transacao antes de alterar reservas, jornada ou folgas. */
    public Map<String,Object> lock(UUID tenant,UUID id) {
        return db.one("SELECT * FROM professionals WHERE tenant_id=? AND id=? FOR UPDATE",tenant,id);
    }
}
