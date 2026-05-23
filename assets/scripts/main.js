Events.on(EventType.ClientLoadEvent, () => {

let build = Blocks.launchPad.buildType.get().getClass()

    Blocks.launchPad.buildType = prov(() => new JavaAdapter(build, {

        buildConfiguration(table) {


            if (Vars.state.isCampaign && !Vars.net.client()) {

                table.table(cons(t => {
                    t.left().top().defaults().fillX().height(40).pad(2);

                    Vars.content.planets()
                        .select(p => p.startSector != 0 && p.accessible)
                        .each(planet => {
                            t.button(planet.localizedName, Styles.flatt, () => {
                                let startSector = planet.sectors.get(planet.startSector);
                                if (startSector != null) {
                                    Vars.ui.planet.showSelect(startSector, cons(selected => {
                                        if (selected != null) {
                                            // 安全地设置目标
                                            if (Vars.state.rules.sector != null && Vars.state.rules.sector.info != null) {
                                                Vars.state.rules.sector.info.destination = selected;
                                            }
                                            Vars.ui.showInfoToast("目标: " + planet.localizedName + " - " + selected.name(), 2);
                                        }
                                    }));
                                } else {
                                    Vars.ui.showInfo("该星球暂无可用扇区");
                                }
                                this.deselect();
                            }).width(200).pad(4).left().row();
                        });
                })).width(200).pad(4).left().row();

                table.table(cons(info => {
                    info.left().defaults().left();
                    info.label(() => "当前目标:").padRight(4);

                    // 修复：添加安全检查和默认值
                    info.label(() => {
                        // 安全检查所有可能为null的路径
                        if (Vars.state == null || Vars.state.rules == null ||
                            Vars.state.rules.sector == null || Vars.state.rules.sector.info == null) {
                            return "[gray]未选择[]";
                        }

                        let dest = Vars.state.rules.sector.info.destination;
                        if (dest == null || dest.planet == null) {
                            return "[gray]未选择[]";
                        }

                        let planetName = dest.planet.localizedName || "未知星球";
                        let sectorName = dest.hasBase() ? dest.name() : "未探索";

                        return "[accent]" + planetName + " - " + sectorName + "[]";
                    }).wrap().width(160);
                })).pad(4).left().row();

            } else {
                this.deselect();
            }

        }

    }, Blocks.launchPad));

});