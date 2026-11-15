package com.yongdrel.reportingservice.application.port.in;

import com.yongdrel.reportingservice.application.model.SavedReportCommand;
import com.yongdrel.reportingservice.domain.model.Caller;
import com.yongdrel.reportingservice.domain.model.SavedReport;

import java.util.List;

public interface SavedReportUseCase {

    SavedReport create(SavedReportCommand command, Caller caller);

    List<SavedReport> list(Caller caller);

    SavedReport get(Long id, Caller caller);

    SavedReport update(Long id, SavedReportCommand command, Caller caller);

    void delete(Long id, Caller caller);

    /** Runs the saved definition and returns the matching report object. */
    Object run(Long id, Caller caller);
}
