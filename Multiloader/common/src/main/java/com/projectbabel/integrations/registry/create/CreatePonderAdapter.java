package com.projectbabel.integrations.registry.create;

import com.projectbabel.integrations.registry.ModIntegrationAdapter;
import com.projectbabel.integrations.registry.UiRefreshService;

import java.util.List;

/** Adapter placeholder for Create Ponder localization hooks. */
public final class CreatePonderAdapter implements ModIntegrationAdapter {

    @Override
    public String id() {
        return "createponder";
    }

    @Override
    public String displayName() {
        return "Create Ponder";
    }

    @Override
    public List<String> modIds() {
        return List.of("create");
    }

    @Override
    public void refreshOpenUi(String reason) {
        UiRefreshService.requestCurrentScreenRefresh(
            displayName(),
            reason,
            "ponder",
            "create"
        );
    }
}
