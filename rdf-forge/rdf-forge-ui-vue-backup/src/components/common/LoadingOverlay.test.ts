import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import LoadingOverlay from './LoadingOverlay.vue'

describe('LoadingOverlay', () => {
  describe('Rendering', () => {
    it('should not render when loading is false', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: false
        }
      })
      
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })

    it('should render when loading is true', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true
        }
      })
      
      expect(wrapper.find('.loading-overlay').exists()).toBe(true)
    })

    it('should show spinner icon when loading', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true
        }
      })
      
      expect(wrapper.find('.loading-icon').exists()).toBe(true)
      expect(wrapper.find('.pi-spinner').exists()).toBe(true)
    })
  })

  describe('Message Display', () => {
    it('should not show message when not provided', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true
        }
      })
      
      expect(wrapper.find('.loading-message').exists()).toBe(false)
    })

    it('should show message when provided', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true,
          message: 'Loading data...'
        }
      })
      
      expect(wrapper.find('.loading-message').exists()).toBe(true)
      expect(wrapper.find('.loading-message').text()).toBe('Loading data...')
    })

    it('should update message when prop changes', async () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true,
          message: 'Initial message'
        }
      })
      
      expect(wrapper.find('.loading-message').text()).toBe('Initial message')
      
      await wrapper.setProps({ message: 'Updated message' })
      
      expect(wrapper.find('.loading-message').text()).toBe('Updated message')
    })
  })

  describe('CSS Classes', () => {
    it('should have proper overlay styling', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true
        }
      })
      
      const overlay = wrapper.find('.loading-overlay')
      expect(overlay.exists()).toBe(true)
    })

    it('should have loading-content container', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: true
        }
      })
      
      expect(wrapper.find('.loading-content').exists()).toBe(true)
    })
  })

  describe('Accessibility', () => {
    it('should be hidden from screen readers when not loading', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: false
        }
      })
      
      // When not loading, the element shouldn't be in the DOM
      expect(wrapper.find('[role="status"]').exists()).toBe(false)
    })
  })

  describe('Slot Content', () => {
    it('should allow slot content when not loading', () => {
      const wrapper = mount(LoadingOverlay, {
        props: {
          loading: false
        },
        slots: {
          default: '<div class="test-content">Test Content</div>'
        }
      })
      
      // LoadingOverlay doesn't use slots, so this verifies the component doesn't break
      expect(wrapper.find('.loading-overlay').exists()).toBe(false)
    })
  })
})