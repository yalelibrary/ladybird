package edu.yale.library.ladybird.engine.imports;

import edu.yale.library.ladybird.engine.model.FunctionConstants;
import edu.yale.library.ladybird.entity.Object;
import edu.yale.library.ladybird.entity.ObjectBuilder;
import edu.yale.library.ladybird.persistence.dao.ObjectDAO;
import edu.yale.library.ladybird.persistence.dao.hibernate.ObjectHibernateDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class OidMinter {
    Logger logger = LoggerFactory.getLogger(this.getClass());

    ObjectDAO objectDAO = new ObjectHibernateDAO();

    public ImportEntityValue write(final ImportEntityValue importEntityValue, int projectId) {
        final List<ImportEntity.Column> exheadList = importEntityValue.getHeaderRow().getColumns();
        final ImportEntity.Column<String> column = new ImportEntity().new Column(FunctionConstants.F1, "");
        exheadList.add(column);
        importEntityValue.setHeaderRow(exheadList);
        final List<ImportEntity.Row> rowList = importEntityValue.getContentRows();

        for (ImportEntity.Row row: rowList) {
            Object object = new ObjectBuilder().createObject();
            object.setDate(new Date());
            object.setProjectId(projectId);

            try {
                final int id = objectDAO.save(object);
                row.getColumns().add(new ImportEntity().new Column(FunctionConstants.F1, String.valueOf(id)));
            } catch (Exception e) {
                logger.error("Error creating oid", e);
                throw e;
            }
        }

        logger.debug("Wrote oids={}", rowList.size());

        importEntityValue.setContentRows(rowList);
        return importEntityValue;
    }
}
