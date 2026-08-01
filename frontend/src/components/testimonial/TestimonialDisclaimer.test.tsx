import {render, screen} from '@testing-library/react';
import TestimonialDisclaimer from './TestimonialDisclaimer';

test('zeigt den rechtlichen Hinweis zu subjektiven Erfahrungsberichten', () => {
  render(<TestimonialDisclaimer/>);

  expect(screen.getByText(/keine wissenschaftlich belegten Aussagen/i)).toBeInTheDocument();
  expect(screen.getByText(/Heilversprechen/i)).toBeInTheDocument();
});
