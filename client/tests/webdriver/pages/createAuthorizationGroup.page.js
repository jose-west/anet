import Page from './page'

const Page_URL = '/admin/authorizationGroups/new'

class CreateAuthorizationGroup extends Page {
    get form()                   { return browser.element('form') }
    get alertSuccess()           { return browser.element('.alert-success') }
    get name()                   { return browser.element('#name') }
    get description()            { return browser.element('#description') }
    get statusActiveButton()     { return browser.element('#statusActiveButton') }
    get statusInactiveButton()   { return browser.element('#statusInactiveButton') }
    get positions()              { return browser.element('#positions') }
    get positionsAutocomplete()  { return browser.element('#react-autowhatever-1--item-0') }
    get submitButton()           { return browser.element('#formBottomSubmit') }

    open() {
        // Only admin users can create authorization groups
        super.openAsAdminUser(Page_URL)
    }

    waitForAlertSuccessToLoad() {
        if(!this.alertSuccess.isVisible()) {
            this.alertSuccess.waitForExist()
            this.alertSuccess.waitForVisible()
        }
    }

    waitForPositionsAutoCompleteToChange(value) {
      this.positionsAutocomplete.waitForExist()
      return browser.waitUntil( () => {
          return this.positionsAutocomplete.getText() === value
        }, 5000, 'Expected autocomplete to contain "' + value +'" after 5s')
  }

    submitForm() {
        this.submitButton.click()
    }
}

export default new CreateAuthorizationGroup()
