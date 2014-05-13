package edu.yale.library.ladybird.web.view;


import edu.yale.library.ladybird.engine.cron.ImportEngineQueue;
import edu.yale.library.ladybird.engine.imports.ImportRequestEvent;
import edu.yale.library.ladybird.engine.imports.SpreadsheetFile;
import edu.yale.library.ladybird.engine.imports.SpreadsheetFileBuilder;
import edu.yale.library.ladybird.entity.Monitor;
import edu.yale.library.ladybird.engine.cron.ExportScheduler;
import edu.yale.library.ladybird.engine.cron.FilePickerScheduler;
import edu.yale.library.ladybird.engine.cron.ImportScheduler;
import edu.yale.library.ladybird.engine.CronSchedulingException;
import edu.yale.library.ladybird.persistence.dao.MonitorDAO;
import org.hibernate.HibernateException;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.faces.application.FacesMessage;
import javax.faces.bean.ApplicationScoped;
import javax.faces.bean.ManagedBean;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.slf4j.LoggerFactory.getLogger;

@ManagedBean
@ApplicationScoped
@SuppressWarnings("unchecked")
public class MonitorView extends AbstractView {
    private final Logger logger = getLogger(this.getClass());

    private static final String IMPORT_JOB_ID = "import_job";
    private static final String IMPORT_JOB_TRIGGER = "trigger";
    private static final String EXPORT_JOB_ID = "export_job";
    private static final String EXPORT_JOB_TRIGGER = "trigger_export";
    private static final String FILEPICKER_JOB_ID = "pickup_job_";
    private static final String FILEPICKER_JOB_TRIGGER = "trigger";
    private static final String FILEPACKER_JOB_GROUP_ID = "group";

    private List<Monitor> itemList;
    private Monitor monitorItem = new Monitor();

    private UploadedFile uploadedFile;
    private String uploadedFileName;

    @Inject
    private MonitorDAO monitorDAO;

    @PostConstruct
    public void init() {
        initFields();
        dao = monitorDAO;
    }

    public void save() {
        try {
            logger.debug("Saving import/export pair=" + monitorItem.toString());

            int itemId = dao.save(monitorItem);

            logger.debug("Scheduling file pick, import, export jobs");

            /* FIXME. Done because currently Import/Export Engine notification e-mail is not tied to a particular User.
               It just asks for user e-mail. When it's linked, a drop down should appear, obviating the need for this
               line.*/

            monitorItem.getUser().setEmail(monitorItem.getNotificationEmail());

            // Set off file monitoring for the particular directory (assumes new scheduler per directory):
            FilePickerScheduler filePickerScheduler = new FilePickerScheduler();
            filePickerScheduler.schedulePickJob(FILEPICKER_JOB_ID + itemId, //some uuid?
                    FILEPICKER_JOB_TRIGGER + itemId, FILEPACKER_JOB_GROUP_ID
                    + itemId, getFilePickupCronString(), monitorItem);

            // Run import export cron pair on this directory (associated with user) from now on
            // Set off import cron:
            ImportScheduler importScheduler = new ImportScheduler();
            importScheduler.scheduleJob(IMPORT_JOB_ID, IMPORT_JOB_TRIGGER, getImportCronSchedule());

            // Set off export cron:
            ExportScheduler exportScheduler = new ExportScheduler();
            exportScheduler.scheduleJob(EXPORT_JOB_ID, EXPORT_JOB_TRIGGER, getExportCronSchedule(), monitorItem);
        } catch (CronSchedulingException e) {
            logger.error("Error scheduling import/export job", e); //ignore exception
        } catch (HibernateException h) {
            logger.error("Error saving import/export pair", h); //ignore exception
        }
    }

    private String getFilePickupCronString() {
        return "0/60 * * * * ?";
    }

    private String getExportCronSchedule() {
        return "0/60 * * * * ?";
    }

    private String getImportCronSchedule() {
        return "0/60 * * * * ?";
    }

    public List getItemList() {
        List<Monitor> monitorList = dao.findAll();
        return monitorList;
    }

    public Monitor getMonitorItem() {
        return monitorItem;
    }

    public void setMonitorItem(Monitor monitorItem) {
        this.monitorItem = monitorItem;
    }

    @Override
    public String toString() {
        return monitorItem.toString();
    }

    public void handleFileUpload(FileUploadEvent event) {
        final Map sessionMap = FacesContext.getCurrentInstance().getExternalContext().getSessionMap();
        this.uploadedFile = event.getFile();
        this.uploadedFileName = uploadedFile.getFileName();
        sessionMap.put("PrimeFacesUploadedFile", uploadedFile);
        sessionMap.put("PrimeFacesUploadedFileName", uploadedFile.getFileName());
        FacesContext.getCurrentInstance().addMessage(null, new FacesMessage("File uploaded, etc."));
    }

    public String process() {
        logger.debug("Processing file={}", uploadedFileName);

        try {
            logger.debug("Saving import/export pair=" + monitorItem.toString());

            int itemId = dao.save(monitorItem);

            logger.debug("Scheduling import, export jobs");

            monitorItem.setDirPath("local");

            monitorItem.getUser().setEmail(monitorItem.getNotificationEmail());

            //Duplicate of FilePickerJob.class

            final SpreadsheetFile file = new SpreadsheetFileBuilder()
                    .setFileName(uploadedFileName)
                    .setAltName(uploadedFileName)
                    .setFileStream(uploadedFile.getInputstream())
                    .createSpreadsheetFile();

            final ImportRequestEvent importEvent = new ImportRequestEvent(file, monitorItem);

            logger.debug("Prepared event=" + importEvent.toString());

            ImportEngineQueue.addJob(importEvent);

            logger.debug("Enqueued event=" + importEvent.toString());

            // Set off import cron:
            ImportScheduler importScheduler = new ImportScheduler();
            importScheduler.scheduleJob(IMPORT_JOB_ID, IMPORT_JOB_TRIGGER, getImportCronSchedule());

            // Set off export cron:
            ExportScheduler exportScheduler = new ExportScheduler();
            exportScheduler.scheduleJob(EXPORT_JOB_ID, EXPORT_JOB_TRIGGER, getExportCronSchedule(), monitorItem);

            return "ok";
        } catch (CronSchedulingException e) {
            logger.error("Error scheduling import/export job", e); //ignore exception
        } catch (HibernateException h) {
            logger.error("Error saving import/export pair", h); //ignore exception
        } catch (IOException io) {
            logger.error("IOException saving import/export pair", io);
        }
        return "failed";
    }

}


