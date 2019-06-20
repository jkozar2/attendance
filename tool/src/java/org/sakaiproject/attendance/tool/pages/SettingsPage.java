/*
 *  Copyright (c) 2017, University of Dayton
 *
 *  Licensed under the Educational Community License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *              http://opensource.org/licenses/ecl2
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.sakaiproject.attendance.tool.pages;


import org.apache.wicket.AttributeModifier;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior;
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox;
import org.apache.wicket.ajax.markup.html.form.AjaxSubmitLink;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.model.ResourceModel;
import org.sakaiproject.attendance.model.AttendanceSite;
import org.sakaiproject.attendance.tool.panels.AttendanceCommentFormPanel;
import org.sakaiproject.attendance.tool.panels.AttendanceGradeFormPanel;
import org.sakaiproject.attendance.tool.panels.AttendanceStatusFormPanel;
import org.sakaiproject.attendance.tool.panels.GradingRulesPanel;
import org.sakaiproject.attendance.tool.panels.util.GradebookItemNameValidator;

/**
 * SettingsPage gets various settings which affect the whole AttendanceSite
 *
 * @author Leonardo Canessa [lcanessa1 (at) udayton (dot) edu]
 * @author David Bauer [dbauer1 (at) udayton (dot) edu]
 * @author Steve Swinsburg (steve.swinsburg@gmail.com)
 */
public class SettingsPage extends BasePage {
	private static final long serialVersionUID = 1L;
	private                 boolean         previousSendToGradebook;
	private                 String          previousName;
	private                 Double          previousMaxGrade;
	private        final IModel<Boolean> useAutoGrading              = new Model<>();
	private        final    IModel<Boolean> autoGradeBySubtraction      = new Model<>();
	private GradingRulesPanel gradingRulesPanel;
	private                 WebMarkupContainer autoGradingTypeContainer;
	
	public SettingsPage() {
		disableLink(settingsLink);

		if(this.role != null && this.role.equals("Student")) {
			throw new RestartResponseException(StudentView.class);
		}

		Label headerSettings = new Label("header-settings",	new ResourceModel("attendance.settings.header"));
		add(headerSettings);
		//Model<AttendanceSite> siteModel = new Model<>(attendanceLogic.getCurrentAttendanceSite());
		//Form<AttendanceSite> settingsForm = new Form<~>("settings-form", new CompoundPropertyModel<>(attendanceLogic.getCurrentAttendanceSite())
		Form<AttendanceSite> settingsForm = new Form<AttendanceSite>("settings-form", new CompoundPropertyModel<>(attendanceLogic.getCurrentAttendanceSite())){
			@Override
			protected void onSubmit() {
				AttendanceSite aS = (AttendanceSite) getDefaultModelObject();
				aS.setAttendanceStatuses();
				aS.setUseAutoGrading(useAutoGrading.getObject());
				aS.setAutoGradeBySubtraction(autoGradeBySubtraction.getObject());

				if(aS.getMaximumGrade() == null && previousMaxGrade != null) {
					aS.setSendToGradebook(false);
					aS.setUseAutoGrading(false);
				}

				boolean result = attendanceLogic.updateAttendanceSite(aS);

				if (result) {
					if(aS.getSendToGradebook()){
						if(previousSendToGradebook) { // if previously true, see if any relevant values have changed
							if(!previousName.equals(aS.getGradebookItemName()) || !previousMaxGrade.equals(aS.getMaximumGrade())){
								attendanceGradebookProvider.update(aS);
							}

							previousName = aS.getGradebookItemName();
						} else {
							attendanceGradebookProvider.create(aS);
						}
					} else {
						if(previousSendToGradebook) {
							attendanceGradebookProvider.remove(aS);
						}
					}

					previousMaxGrade = aS.getMaximumGrade();
					previousSendToGradebook = aS.getSendToGradebook();

					// Successful Save - Regrade All if Auto Grade is set to true and maximum points is set
					if (aS.getUseAutoGrading() != null && aS.getUseAutoGrading() && aS.getMaximumGrade() > 0) {
						attendanceLogic.regradeAll(aS);
					}

					getSession().info(getString("attendance.settings.grading.success"));
				} else {
					getSession().error(getString("attendance.settings.grading.failure"));
				}
				//super.onSubmit(target, form);
				//target.add(feedbackPanel);
			}
		};

		settingsForm.add(createEditStatusesPanel());
		settingsForm.add(createEditCommentPanel());
		createEditGradePanel(settingsForm);

		AjaxSubmitLink submit = new AjaxSubmitLink("submit-link") {
			@Override
			protected void onError(AjaxRequestTarget target, Form<?> form) {
				target.add(feedbackPanel);
			}

			@Override
			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {
				super.onSubmit(target, form);
				target.add(feedbackPanel);
			}
		};
		settingsForm.add(submit);
		// Grade rules container
		this.gradingRulesPanel = new GradingRulesPanel("grading-rules");
		this.gradingRulesPanel.setOutputMarkupPlaceholderTag(true);
		add(this.gradingRulesPanel);
		add(settingsForm);
	}

	private WebMarkupContainer createEditStatusesPanel() {
		WebMarkupContainer allStatusesContainer = new WebMarkupContainer("all-statuses-container");
		allStatusesContainer.add(new AttendanceStatusFormPanel("edit-status-panel", feedbackPanel));
		return allStatusesContainer;
	}

	private WebMarkupContainer createEditCommentPanel() {
		WebMarkupContainer commentContainer = new WebMarkupContainer("comment-container");
		commentContainer.add(new AttendanceCommentFormPanel("edit-comment-panel", feedbackPanel));
		return commentContainer;
	}

	private void createEditGradePanel(Form<AttendanceSite> settingsForm) {
		final AttendanceSite aS = attendanceLogic.getCurrentAttendanceSite();
		this.previousSendToGradebook = aS.getSendToGradebook();
		this.previousName = aS.getGradebookItemName();
		this.previousMaxGrade = aS.getMaximumGrade();
		this.useAutoGrading.setObject(aS.getUseAutoGrading());
		this.autoGradeBySubtraction.setObject(aS.getAutoGradeBySubtraction());

		final WebMarkupContainer grading = new WebMarkupContainer("grading");
		grading.setOutputMarkupPlaceholderTag(true);
		Label maxGradeLabel = new Label("maximum-grade-label", new ResourceModel("attendance.settings.grading.max.points.possible"));
		NumberTextField<Double> maximum = new NumberTextField<Double>("maximumGrade");
		maximum.setMinimum(0.1);
		maximum.setStep(0.1);
		settingsForm.add(maximum);
		settingsForm.add(grading);
		settingsForm.add(maxGradeLabel);
		Label isGradeShownLabel = new Label("is-grade-shown-label", new ResourceModel("attendance.settings.grading.is.grade.shown"));
		CheckBox isGradeShown = new CheckBox("isGradeShown");
		grading.add(isGradeShown);
		grading.add(isGradeShownLabel);

		final WebMarkupContainer gradebook = new WebMarkupContainer("gradebook");
		gradebook.setOutputMarkupPlaceholderTag(true);
		Label gbItemName = new Label("gradebook-item-name", new ResourceModel("attendance.settings.grading.gradebook.item.name"));
		TextField<String> gradebookItemName = new TextField<String>("gradebookItemName");
		gradebookItemName.add(new GradebookItemNameValidator(aS, aS.getGradebookItemName()));
		gradebookItemName.setRequired(true);
		gradebook.add(gbItemName);
		gradebook.add(gradebookItemName);
		grading.add(gradebook);
		final boolean gradebookDefined = attendanceGradebookProvider.isGradebookDefined(aS.getSiteID());
		final AjaxCheckBox sendToGradebook = new AjaxCheckBox("sendToGradebook", Model.of(this.previousSendToGradebook)) {
			@Override
			protected void onUpdate(AjaxRequestTarget target) {
				int thing = 0;
			}
		};
		Label sendToGBLabel = new Label("send-to-gradebook", new ResourceModel("attendance.settings.grading.send.to.gradebook"));
		grading.add(sendToGradebook);
		grading.add(sendToGBLabel);
		final RadioGroup<Boolean> useAutoGradingGroup = new RadioGroup<>("use-auto-grading-group", this.useAutoGrading);
		useAutoGradingGroup.setRenderBodyOnly(false);
		grading.add(useAutoGradingGroup);
		Radio<Boolean> manualGrading = new Radio<>("manual-grading", Model.of(Boolean.FALSE));
		Radio<Boolean> autoGrading = new Radio<>("auto-grading", Model.of(Boolean.TRUE));
		useAutoGradingGroup.add(manualGrading);
		useAutoGradingGroup.add(autoGrading);
		this.autoGradingTypeContainer = new WebMarkupContainer("auto-grading-type");
		this.autoGradingTypeContainer.setOutputMarkupPlaceholderTag(true);
		grading.add(this.autoGradingTypeContainer);
		final RadioGroup<Boolean> autoGradeType = new RadioGroup<>("auto-grading-type-group", this.autoGradeBySubtraction);
		autoGradeType.setRenderBodyOnly(false);
		this.autoGradingTypeContainer.add(autoGradeType);
		Radio<Boolean> subtractGrading = new Radio<>("subtract-grading", Model.of(Boolean.TRUE));
		Radio<Boolean> addGrading = new Radio<>("add-grading", Model.of(Boolean.FALSE));
		autoGradeType.add(subtractGrading);
		autoGradeType.add(addGrading);
		settingsForm.add(grading);

		//return new AttendanceGradeFormPanel("grade-settings-panel", feedbackPanel);
	}
}
